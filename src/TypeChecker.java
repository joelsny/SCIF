import java.io.*;

import ast.*;
import java.util.Map;
import java_cup.runtime.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import parser.Parser;
import sherrloc.diagnostic.SherrlocDiagnoser;
import sherrloc.diagnostic.explanation.Explanation;
import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Hypothesis;
import typecheck.sherrlocUtils.Inequality;
import typecheck.sherrlocUtils.Relation;
import typecheck.*;
import parser.*;

import java.util.*;

public class TypeChecker {

    public static void main(String[] args) {
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        //typecheck(inputFile, outputFile);
    }

    /*
        Given a list of SCIF source files, this method type-checks all code,
        ignoring information flow control.  It generates constraints in
        SHErrLoc format and put them in outputFile, then runs SHErrLoc to get
        error info.
     */
    public static boolean regularTypecheck(List<SourceFile> roots,
                                                    boolean DEBUG) throws SemanticException, Parser.SyntaxError {
        logger.trace("typecheck starts...");

        if (roots == null) return false;

        // Add built-ins and Collect global info
        NTCEnv ntcEnv = new NTCEnv(null);
        for (SourceFile root : roots) {
            ntcEnv.addSourceFile(root.getSourceFilePath(), root);

            root.passScopeContext(null);
            assert root.ntcGlobalInfo(ntcEnv, null) : "Must succeed or throw a semantic exception";
        }

        // Generate constraints
        for (SourceFile root : roots) {
            if (!root.isBuiltIn() && root instanceof ContractFile) {
                root.genTypeConstraints(ntcEnv, null);
            }
        }

        List<Constraint> cons = ntcEnv.cons();

        SherrlocDiagnoser sherrlocDiagnoser = Utils.createDiagnoser(ntcEnv.getTypeSet(), ntcEnv.getTypeRelationCons(),
                cons, false, null);
        try {
            if (DEBUG) {
                System.err.println("regular type-checking using SLC...");
            }
            if (!runSLC(ntcEnv.programMap(), sherrlocDiagnoser, DEBUG)) {
                return false;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean ifcTypecheck(List<SourceFile> roots,
                                       boolean DEBUG)
            throws SemanticException
    {

//        Map<SourceFile, File> outputFileMap = new HashMap<>();
//        for (int i = 0; i < roots.size(); ++i) {
//            outputFileMap.put(roots.get(i), outputFiles.get(i));
//        }

        // HashMap<String, ContractSym> contractMap = new HashMap<>();
        SymTab contractMap = new SymTab();
        List<String> contractNames = new ArrayList<>();
        //HashSet<String> principalSet = new HashSet<>();
        //env.principalSet.add("this");

        //ArrayList<Constraint> cons = new ArrayList<>();

        for (SourceFile root : roots) {
            String contractName = root.getContractName();
            if (contractNames.contains(contractName)) {
                throw new SemanticException("Contract name already defined: " + contractName,
                        new CodeLocation(root.getSourceFilePath()));
            }
            if (root instanceof ContractFile) {
                try {
                    ContractSym contractSym = new ContractSym(root.getContractName(),
                            ((ContractFile) root).getContract());

                    contractNames.add(contractSym.getName());
                    contractMap.add(contractSym.getName(), contractSym);
                } catch (SymTab.AlreadyDefined e) {
                    assert false; // cannot happen
                }
            } else {
                try {
                    InterfaceSym interfaceSym = new InterfaceSym(root.getContractName(),
                            ((InterfaceFile) root).getInterface());

                    contractNames.add(interfaceSym.getName());
                    contractMap.add(interfaceSym.getName(), interfaceSym);
                } catch (SymTab.AlreadyDefined e) {
                    throw new SemanticException("Contract name already defined: " + contractName,
                            new CodeLocation(root.getContractName()));
                }
            }
        }

        // logger.debug("contracts: \n" + contractMap.getTypeSet());
        int idx = 0;
        for (SourceFile root : roots) {
            InterfaceSym contractSym = (InterfaceSym) contractMap.lookup(contractNames.get(idx));
            ++idx;
            contractSym.symTab = new SymTab(contractMap);
            root.globalInfoVisit(contractSym);
            // logger.debug(contractSym.toString());
            //root.findPrincipal(principalSet);
        }

        // logger.debug("starting to ifc typecheck");

        VisitEnv env = new VisitEnv(
                new Context(),
                new ArrayList<>(),
                new ArrayList<>(),
                contractMap,
                contractMap,
                new Hypothesis(),
                new HashSet<>(),
                null,
                new HashMap<>()
        );

        // env.globalSymTab = env.curSymTab = contractMap;

        // collect constraints generating for signatures for each contract
        // a map from filename -> contract name -> signature info
        /*for (Program root : roots) {
            buildSignatureConstraints(root, env);
        }*/

        for (SourceFile root : roots)
            if (root instanceof ContractFile) {
                // env.programMap.put(root.getContractName(), root);
                env.programMap.computeIfAbsent(root.getContractName(), k -> new ArrayList<SourceFile>()).add(root);
                if (root.isBuiltIn()) continue;
                env.sigReq.clear();
                if (!ifcTypecheck((ContractFile) root, env, DEBUG)) {
                    return false;
                }
            }

        logger.trace("typecheck finishes");
        return true;
    }

    /**
     * Create ifc constraints for the signature of a contract.
     * TODO: more detailed doc
     */
    private static void buildSignatureConstraints(String contractName, VisitEnv env,
                                                  String namespace, String curContractName) {
        List<Constraint> cons = env.cons;
        // List<Constraint> trustCons = env.trustCons;
        // String contractName = root.getContractName();//contractNames.get(fileIdx);
        InterfaceSym contractSym = env.getContract(contractName);
        // logger.debug("current Contract: " + contractName + "\n" + contractSym + "\n"
                // + env.curSymTab.getTypeSet());
        // generate trust relationship dec constraints

//        if (!namespace.equals("")) {
//            env.addTrustConstraint(
//                    new Constraint(new Inequality(namespace, Relation.EQ, namespace + "..this"),
//                            null, curContractName,
//                            "TODO"));
//        }
        // String ifNameContract = contractSym.getLabelNameContract();
        // String ifContract = contractSym.getLabelContract();
        if (namespace.equals(curContractName)) {
//            cons.add(new Constraint(new Inequality(ifNameContract, Relation.EQ, ifContract),
//                    Utils.placeholder(), contractName,
//                    "The Code integrity label of contract " + ifNameContract + " may be incorrect"));

            for (Assumption assumption : contractSym.assumptions()) {
                env.addTrustConstraint(new Constraint(
                        assumption.toInequality(),
                        assumption.location(),
                        "Static trust relationship"));
            }
        } else {
            // TODO: handle the assumptions of other contracts
        }

        env.setCurContract(contractSym);
        // env.curSymTab.setParent(env.globalSymTab);//TODO

        for (Map.Entry<String, FuncSym> funcPair : contractSym.symTab.getFuncs().entrySet()) {
            FuncSym func = funcPair.getValue();
//            // logger.debug("add func's sig constraints: [" + func.funcName + "]");
            //TODO: simplify
            namespace = "";
            String ifNameCallBeforeLabel = func.externalPcSLC();
            String ifNameCallAfterLabel = func.internalPcSLC();
            // String ifNameCallLockLabel = func.getLabelNameCallLock(namespace);
            String ifNameCallGammaLabel = func.getLabelNameCallGamma();
            String ifCallBeforeLabel = func.getCallPcLabel(namespace);
            String ifCallAfterLabel = func.getCallAfterLabel(namespace);
            String ifCallLockLabel = func.getCallLockLabel(namespace);
            // // logger.debug(ifNameCallBeforeLabel + "\n" + ifNameCallAfterLabel + "\n" + ifNameCallLockLabel + "\n" + ifCallAfterLabel + "\n" +ifCallLockLabel);
            if (ifCallBeforeLabel != null) {
                cons.add(new Constraint(
                        new Inequality(ifCallBeforeLabel, Relation.EQ, ifNameCallBeforeLabel),
                        func.external_pc.location(),
                        "Integrity requirement to call this method may be incorrect"));
            }
            if (ifCallAfterLabel != null) {
                cons.add(new Constraint(
                        new Inequality(ifCallAfterLabel, Relation.EQ, ifNameCallAfterLabel),
                        func.internal_pc.location(),
                        "Integrity pc level autoendorsed to when calling this method may be incorrect"));
            }

            if (ifCallLockLabel != null) {
                cons.add(new Constraint(
                        new Inequality(ifCallLockLabel, Relation.EQ, ifNameCallGammaLabel),
                        func.gamma.location(),
                        "The final reentrancy lock label may be declared incorrectly"));

            }

            String ifNameReturnLabel = func.returnSLC();
            String ifReturnLabel = func.getRtnValueLabel(namespace);
            if (ifReturnLabel != null) {
                cons.add(new Constraint(
                        new Inequality(ifReturnLabel, Relation.EQ, ifNameReturnLabel),
                        func.location,
                        "Integrity label of this method's return value may be incorrect"));
            }

            for (int i = 0; i < func.parameters.size(); ++i) {
                VarSym arg = func.parameters.get(i);
                String ifNameArgLabel = func.getLabelNameArg(i);
//                String ifArgLabel = namespace + "." + arg.getLabelValueSLC();
                String ifArgLabel = arg.labelValueSLC();

                if (ifArgLabel != null) {
                    cons.add(new Constraint(new Inequality(ifNameArgLabel, Relation.EQ, ifArgLabel),
                            arg.location,
                            "Argument " + arg.getName() + " may be labeled incorrectly"));
                    env.addTrustConstraint(
                            new Constraint(new Inequality(ifNameCallBeforeLabel, ifNameArgLabel),
                                    arg.location,
                                    "Argument " + arg.getName()
                                            + " must be no more trusted than caller's integrity"));
                }
            }

        }
        cons.add(new Constraint());
        // env.addSigCons(contractName, trustCons, cons);
    }

    private static boolean ifcTypecheck(ContractFile contractFile, VisitEnv env,
                                        boolean DEBUG) throws SemanticException {
        String contractName = contractFile.getContractName();//contractNames.get(fileIdx);
        InterfaceSym contractSym = env.getContract(contractName);

        env.setCurContract(contractSym);
        env.enterNewContract();

        for (Map.Entry<String, VarSym> varPair : contractSym.symTab.getVars().entrySet()) {
            VarSym var = varPair.getValue();
            String varName = var.labelNameSLC();
            String ifLabel = var.labelValueSLC();
            if (ifLabel != null && varName != null) {
                env.cons.add(
                        new Constraint(new Inequality(varName, Relation.EQ, ifLabel), var.location,
                                "Variable " + var.getName() + " may be labeled incorrectly"));
            }
        }

        contractFile.genConsVisit(env, true);
        buildSignatureConstraints(contractFile.getContractName(), env, contractFile.getContractName(),
                contractFile.getContractName());
        env.sigReq.forEach((name, curContractName) -> {
            buildSignatureConstraints(curContractName, env, name, contractFile.getContractName());
        });

        List<Constraint> contractCons = env.getCons(Utils.CONTRACT_KEYWORD), contractTrustCons = env.getTrustCons(Utils.CONTRACT_KEYWORD);


        for (String methodName : env.getMethodNameSet()) {
            if (methodName.equals(Utils.CONTRACT_KEYWORD)) continue;

            List<Constraint> cons = new ArrayList<>();
            List<Constraint> trustCons = new ArrayList<>();
            cons.addAll(contractCons);
            cons.addAll(env.getCons(methodName));
            trustCons.addAll(contractTrustCons);
            trustCons.addAll(env.getTrustCons(methodName));

            SherrlocDiagnoser sherrlocDiagnoser = Utils.createDiagnoser(env.principalSet(), trustCons, cons,
                    true, contractSym);
            boolean result;
            try {
                result = runSLC(env.programMap, sherrlocDiagnoser, DEBUG);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return false;
            }
            if (!result) return false;
        }
        return true;
    }


    static boolean runSLC(Map<String, List<SourceFile>> programMap, SherrlocDiagnoser sherrlocDiagnoser,
                          boolean DEBUG) {
//        logger.trace("running SLC");
        if (sherrlocDiagnoser == null) { // There were no constraints
            return true;
        }

//        String classDirectoryPath = new File(
//                SCIF.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        sherrloc.diagnostic.DiagnosticConstraintResult result = sherrlocDiagnoser.getConstraintResult();
//      System.err.println("runSLC: " + outputFileName + " " + result.success());
//        System.err.println(Arrays.toString(Utils.runSLCCMD(classDirectoryPath, outputFileName)));
//        // logger.debug("runSLC: " + result);
        if (result.success()) {
            // System.out.println(Utils.TYPECHECK_PASS_MSG);
        } else {
            System.out.println(Utils.TYPECHECK_ERROR_MSG);
            double best = Double.MAX_VALUE;
            boolean seced = false;
            System.out.println("Code locations most likely to be wrong:");
            if (DEBUG) {
                System.out.println("Number of places: " + result.getSuggestions().size());
            }
            int idx = 0;
            Set<String> expSet = new HashSet<>();
            for (int i = 0; i < result.getSuggestions().size(); ++i) {
                // if (i > 0) continue; // only output the first suggestion
                Explanation explanation = result.getSuggestions().get(i);
                double weight = result.getSuggestions().get(i).getWeight();
                if (best > weight) {
                    best = weight;
                }
                if (!seced && best < weight) {
                    seced = true;
                    System.out.println("Some other possible places:");
                }
                List<String> s = Utils.SLCEntitiesToStrings(programMap, result.getSuggestions().get(i),
                        DEBUG);
                if (s != null) {
                    for (String exp: s) {
                        if (!expSet.contains(exp)) {
                            expSet.add(exp);
                            idx += 1;
                            System.out.println(idx + ". " + exp + "\n");
                        }
                    }
//                    System.out.println(idx + ":");
                }
            }
            return false;
        }
        return true;
    }
    protected static final Logger logger = LogManager.getLogger();
}
