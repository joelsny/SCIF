import java.io.*;

import ast.*;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java_cup.runtime.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sherrloc.diagnostic.explanation.Explanation;
import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Hypothesis;
import typecheck.sherrlocUtils.Inequality;
import typecheck.sherrlocUtils.Relation;
import typecheck.*;
import parser.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TypeChecker {

    public static void main(String[] args) {
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        //typecheck(inputFile, outputFile);
    }

    /*
        parse all SCIF source files and store AST roots in roots.
    */
    public static List<SourceFile> buildRoots(List<File> inputFiles) throws IOException, SemanticException, Parser.SyntaxError {
        List<SourceFile> roots = new ArrayList<>();

        Queue<File> mentionedFiles = new ArrayDeque<>(inputFiles);
        InheritGraph graph = new InheritGraph();
        Map<String, List<SourceFile>> fileMap = new HashMap<>();
        Set<String> includedFilePaths = inputFiles.stream().flatMap(file -> Stream.of(file.getAbsolutePath())).collect(
                Collectors.toSet());
        
        // add all built-in source files
        for (File builtinFile: Utils.BUILTIN_FILES) {
            Symbol result = Parser.parse(builtinFile, null);//p.parse();
            List<SourceFile> rootsFiles = (List<SourceFile>) result.value;
            SourceFile root = (rootsFiles.get(0)).makeBuiltIn();
            if (root instanceof ContractFile) {
                ((ContractFile) root).getContract().clearExtends();
            }
            // TODO root.setName(inputFile.name());
            List<String> sourceCode = Files.readAllLines(Paths.get(builtinFile.getAbsolutePath()),
                    StandardCharsets.UTF_8);

            // sourceCode is only used to show error msgs for SLC - should save all lines from the file path anyway
            root.setSourceCode(sourceCode);
            root.addBuiltIns();
            roots.add(root);
            assert root.ntcAddImportEdges(graph);
            includedFilePaths.add(builtinFile.getAbsolutePath());
            fileMap.put(builtinFile.getAbsolutePath(), new ArrayList<>(List.of(root)));
        }
        while (!mentionedFiles.isEmpty()) {
            File file = mentionedFiles.poll();
            Symbol result;
            result = Parser.parse(file, null);
            assert result != null;

            List<SourceFile> rootsFiles = (List<SourceFile>) result.value;
            assert !rootsFiles.isEmpty();

            fileMap.put((rootsFiles.get(0)).getSourceFilePath(), rootsFiles);
            // TODO root.setName(inputFile.name());
            List<String> sourceCode = Files.readAllLines(Paths.get(file.getAbsolutePath()),
                    StandardCharsets.UTF_8);
            // sourceCode is only used to show error msgs for SLC - should save all lines from the file path anyway
            for (SourceFile root : rootsFiles) {
                root.setSourceCode(sourceCode);
                root.addBuiltIns();
                roots.add(root);
                assert root.ntcAddImportEdges(graph);

                for (String filePath : root.importPaths()) {
                    // only those imported file paths != current file path
                    if (!includedFilePaths.contains(filePath)) {
                        mentionedFiles.add(new File(filePath));
                        includedFilePaths.add(filePath);
                    }
                }
            }
        }

        // TODO do we need to check if there's any non-existent contract name?

        Map<String, List<TopLayerNode>> sourceFileMap = new HashMap<>(); // file path -> list of AST contract/interface

        for (SourceFile root : roots) {
            if (root instanceof ContractFile) {
                sourceFileMap.computeIfAbsent(root.getSourceFilePath(), k -> new ArrayList<>()).add(((ContractFile) root).getContract());
            } else if (root instanceof InterfaceFile) {
                sourceFileMap.computeIfAbsent(root.getSourceFilePath(), k -> new ArrayList<>()).add(((InterfaceFile) root).getInterface());
            } else {
                assert false : root.getContractName();
            }
        }

        List<SourceFile> toporder = new ArrayList<>();
        // code-paste in a topological order
        for (String x : graph.getTopologicalQueue()) {
            List<SourceFile> rootsFile = fileMap.get(x);
            if (rootsFile == null || rootsFile.isEmpty()) {
                assert false;
                return null;
            }

            for (SourceFile rt : rootsFile) {
                toporder.add(rt);
                rt.updateImports(fileMap);
                rt.codePasteContract(x, sourceFileMap);
            }
        }

        return toporder;
    }

    /*
        Given a list of SCIF source files, this method type-checks all code,
        ignoring information flow control.  It generates constraints in
        SHErrLoc format and put them in outputFile, then runs SHErrLoc to get
        error info.
     */
    public static List<SourceFile> regularTypecheck(List<File> inputFiles, File logDir,
                                                    boolean DEBUG) throws IOException, SemanticException, Parser.SyntaxError {
        File outputFile = new File(logDir, SCIF.newFileName("ntc", "cons"));
        logger.trace("typecheck starts...");

        // roots = toporder;
        List<SourceFile> roots;
        roots = buildRoots(inputFiles);
        if (roots == null) return roots;

        // Add built-ins and Collect global info
        NTCEnv ntcEnv = new NTCEnv(null);
        for (SourceFile root : roots) {
            ntcEnv.addSourceFile(root.getSourceFilePath(), root);

            root.passScopeContext(null);
            // System.err.println("Checking contract " + root.getContractName());
            if (!root.ntcGlobalInfo(ntcEnv, null)) {
                assert false : "Must succeed or throw a semantic exception";
            }
        }

        // Generate constraints
        for (SourceFile root : roots) {
            if (!root.isBuiltIn() && root instanceof ContractFile) {
//                ntcEnv.enterFile(root.getSourceFilePath());
                root.genTypeConstraints(ntcEnv, null);
            }
        }

        // Check using SHErrLoc and get a solution
        // logger.debug("generating cons file for NTC");
        // constructors: all types
        // assumptions: none or relations between types
        // constraints
//        List<Constraint> contractCons = ntcEnv.contractCons();
//        for (SourceFile root : roots) {
//            if (!root.isBuiltIn() && root instanceof ContractFile) {
//                String filename = root.getContractName();
//                for (String methodname : ntcEnv.getMethodnames(root.getSourceFilePath())) {
        List<Constraint> cons = ntcEnv.cons();
//                    List<Constraint> cons = new ArrayList<>();
//                    cons.addAll(contractCons);
//                    cons.addAll(ntcEnv.methodCons(root.getSourceFilePath(), methodname));
//                    File outputFile = new File(logDir,
//                            SCIF.newFileName(filename + "." + methodname, "ntc"));
        if (!Utils.writeCons2File(ntcEnv.getTypeSet(), ntcEnv.getTypeRelationCons(),
                cons,
                outputFile, false, null)) {
            return roots;
        }
        try {
            if (DEBUG) {
                System.err.println("regular type-checking using SLC...");
            }
            if (!runSLC(ntcEnv.programMap(), outputFile.getAbsolutePath(), DEBUG)) {
                return null;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }

        return roots;
    }

    public static boolean ifcTypecheck(List<SourceFile> roots, File logDir,
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
                if (!ifcTypecheck((ContractFile) root, env, logDir, DEBUG)) {
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

    private static boolean ifcTypecheck(ContractFile contractFile, VisitEnv env, File logDir,
                                        boolean DEBUG) throws SemanticException {
        String contractName = contractFile.getContractName();//contractNames.get(fileIdx);
        InterfaceSym contractSym = env.getContract(contractName);
        // logger.debug("cururent Contract: " + contractName + "\n" + contractSym + "\n"
                // + env.curSymTab.getTypeSet());

        env.setCurContract(contractSym);
        // env.curSymTab.setParent(env.globalSymTab);//TODO
        env.enterNewContract();
//        env.cons = new ArrayList<>();

        for (Map.Entry<String, VarSym> varPair : contractSym.symTab.getVars().entrySet()) {
            VarSym var = varPair.getValue();
            String varName = var.labelNameSLC();
            // logger.debug(varName);
            String ifLabel = var.labelValueSLC();
            if (ifLabel != null && varName != null) {
                env.cons.add(
                        new Constraint(new Inequality(varName, Relation.EQ, ifLabel), var.location,
                                "Variable " + var.getName() + " may be labeled incorrectly"));

                //env.cons.add(new Constraint(new Inequality(if Label, varName), var.location));

            }
            // logger.debug(": {}", var);
        }

        contractFile.genConsVisit(env, true);
        buildSignatureConstraints(contractFile.getContractName(), env, contractFile.getContractName(),
                contractFile.getContractName());
        env.sigReq.forEach((name, curContractName) -> {
            buildSignatureConstraints(curContractName, env, name, contractFile.getContractName());
        });

        // System.out.println("prinSet size: " + env.principalSet().size());
        List<Constraint> contractCons = env.getCons(Utils.CONTRACT_KEYWORD), contractTrustCons = env.getTrustCons(Utils.CONTRACT_KEYWORD);


        for (String methodName : env.getMethodNameSet()) {
            if (methodName.equals(Utils.CONTRACT_KEYWORD)) continue;
            File outputFile = new File(logDir, SCIF.newFileName(methodName, "ifc"));
            List<Constraint> cons = new ArrayList<>();
            List<Constraint> trustCons = new ArrayList<>();
            cons.addAll(contractCons);
            cons.addAll(env.getCons(methodName));
            trustCons.addAll(contractTrustCons);
            trustCons.addAll(env.getTrustCons(methodName));
            if (!Utils.writeCons2File(env.principalSet(), trustCons, cons, outputFile,
                    true, contractSym)) {
                continue;
            }
            boolean result = false;
            try {
                result = runSLC(env.programMap, outputFile.getAbsolutePath(), DEBUG);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return false;
            }
            if (!result) return false;
        }
        return true;
    }

    static boolean runSLC(Map<String, List<SourceFile>> programMap, String outputFileName,
                          boolean DEBUG) throws Exception {
//        logger.trace("running SLC");


        String classDirectoryPath = new File(
                SCIF.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        sherrloc.diagnostic.DiagnosticConstraintResult result = Utils.runSherrloc(outputFileName);
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
