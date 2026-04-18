package ast;

import compile.CompileEnv;
import compile.ast.Event;
import compile.ast.Function;
import compile.ast.VarDec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import typecheck.*;
import typecheck.exceptions.SemanticException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Contract extends TopLayerNode {

    String contractName;
    String implementsContractName = "";
    String extendsContractName = "";
    //    final boolean extendsContract;
    TrustSetting trustSetting;
    List<StructDef> structDefs;
    List<StateVariableDeclaration> varDeclarations;
    List<ExceptionDef> exceptionDefs;
    List<EventDef> eventDefs;
    private List<FunctionDef> methodDeclarations;

    /*
        fields that are supposed to complete after typechecking
     */
    Map<String, ExceptionTypeSym> exceptionTypeSymMap;
    Map<String, EventTypeSym> eventTypeSymMap;
//
//    public Contract(String contractName, TrustSetting trustSetting,
//            List<StateVariableDeclaration> varDeclarations,
//            List<ExceptionDef> exceptionDefs,
//            List<FunctionDef> methodDeclarations) {
//        this.contractName = contractName;
//        this.trustSetting = trustSetting;
//        this.trustSetting.labelTable.put("this", "address(this)");
//        this.varDeclarations = varDeclarations;
//        this.exceptionDefs = exceptionDefs;
//        this.methodDeclarations = methodDeclarations;
//
//        setDefault();
////        extendsContract = true;
//    }

    public Contract(String contractName,
                    String implementsContractName, String extendsContractName,
                    TrustSetting trustSetting,
                    List<StructDef> structDefs,
                    List<StateVariableDeclaration> varDeclarations,
                    List<ExceptionDef> exceptionDefs,
                    List<EventDef> eventDefs,
                    List<FunctionDef> methodDeclarations) throws SemanticException {
        this.contractName = contractName;
        this.implementsContractName = implementsContractName;
        this.extendsContractName = extendsContractName;
        this.trustSetting = trustSetting;
        this.trustSetting.labelTable.put("this", "address(this)");
        this.structDefs = structDefs;
        this.varDeclarations = varDeclarations;
        this.exceptionDefs = exceptionDefs;
        this.eventDefs = eventDefs;
        this.methodDeclarations = methodDeclarations;
        setDefault();
        // set namespace for exception defs
        for (ExceptionDef exceptionDef : exceptionDefs) {
            exceptionDef.setNamespace(contractName);
        }
//        if (contractName.equals(Utils.BASE_CONTRACT_IMP_NAME)) {
//            System.err.println("debug: show methods in " + contractName);
//            for (FunctionDef functionDef : methodDeclarations) {
//                System.err.println(functionDef.name);
//            }
//        }
    }

    private void setDefault() throws SemanticException {
        if (extendsContractName.isEmpty() && !contractName.equals(Utils.BASE_CONTRACT_IMP_NAME)) {
            extendsContractName = Utils.BASE_CONTRACT_IMP_NAME;
        }
        if (!contractName.equals(Utils.BASE_CONTRACT_IMP_NAME)) {
            // add default constructor
            boolean containsConstructor = false;
            for (FunctionDef functionDef : methodDeclarations) {
                if (functionDef.isConstructor()) {
                    if (containsConstructor) {
                        throw new SemanticException("multiple constructors are not allowed in " + contractName, location);
                    }
                    containsConstructor = true;
                }
            }
            if (!containsConstructor) {
                FunctionDef constructorDef = new FunctionDef(Utils.CONSTRUCTOR_KEYWORD, new FuncLabels(), new Arguments(),
                        List.of(new CallStatement(new Call(new Name(Utils.SUPER_KEYWORD), new ArrayList<>()))),
                        new ArrayList<>(), null, true, location);
//                System.err.println("Adding default constructor for " + contractName + " " + constructorDef.body.size());
                methodDeclarations.add(0, constructorDef);
            }
        }
    }
//
//    public boolean ntcInherit(InheritGraph graph) {
//        // add an edge from superclass to this contract
//        if (!superContractName.isEmpty()) {
//            graph.addEdge(superContractName, contractName);
//        }
//        return true;
//    }

    public boolean ntcGlobalInfo(NTCEnv env, ScopeContext parent)
            throws SemanticException {
        ScopeContext now = new ScopeContext(this, parent);
        // SymTab curSymTab = new SymTab(env.curSymTab());
        env.enterNewScope();
        Utils.addBuiltInTypes(env.curSymTab());
        VarSym anySym = (VarSym) env.getCurSym(Utils.LABEL_BOTTOM);
        // env.initSymTab(curSymTab);
        ContractSym contractSym = new ContractSym(contractName, env.curSymTab(), new ArrayList<>(), this, anySym);
        env.addContractSym(env.currentSourceFileFullName(), env.currentContractName(), contractSym);
        try {
            env.addSym(contractName, contractSym);
        } catch (SymTab.AlreadyDefined e) {
            assert false; // cannot happen
        }
        env.setCurContractSym(contractSym);
        // Utils.addBuiltInASTNode(contractSym, env.globalSymTab(), trustSetting);

        for (StructDef def: structDefs) {
            if (!def.ntcGlobalInfo(env, now)) {
                return false;
            }
        }

        for (StateVariableDeclaration dec : varDeclarations) {
            if (!dec.ntcGlobalInfo(env, now)) {
                return false;
            }
        }

        for (ExceptionDef def : exceptionDefs) {
            if (!def.ntcGlobalInfo(env, now)) {
                return false;
            }
        }

        for (EventDef def : eventDefs) {
            if (!def.ntcGlobalInfo(env, now)) {
                return false;
            }
        }

        for (FunctionDef fDef : methodDeclarations) {
            if (!fDef.ntcGlobalInfo(env, now)) {
                return false;
            }
        }
        exceptionTypeSymMap = env.getExceptionTypeSymMap();
        eventTypeSymMap = env.getEventTypeSymMap();
        env.exitNewScope();
        return true;
    }

    @Override
    public ScopeContext genTypeConstraints(NTCEnv env, ScopeContext parent) throws SemanticException {
        // System.err.println("entering contract: " + contractName);
        ScopeContext now = new ScopeContext(this, parent);
        env.setCurContractSym(env.getContract(contractName));

        for (StructDef def: structDefs) {
            def.genTypeConstraints(env, now);
        }
        for (StateVariableDeclaration dec : varDeclarations) {
            dec.genTypeConstraints(env, now);
        }

        trustSetting.genTypeConstraints(env, now);

        for (ExceptionDef def : exceptionDefs) {
            def.genTypeConstraints(env, now);
        }

        for (EventDef def : eventDefs) {
            def.genTypeConstraints(env, now);
        }

        for (FunctionDef fDef : methodDeclarations) {
            fDef.genTypeConstraints(env, now);
        }

        // System.err.println("exiting contract: " + contractName);
        return now;
    }

    @Override
    public void globalInfoVisit(InterfaceSym contractSym) throws SemanticException {
        // contractSym.name = contractName;
//        contractSym.trustSetting = trustSetting;
        // contractSym.ifl = ifl;
        // contractSym.addContract(contractName, contractSym);
        Utils.addBuiltInTypes(contractSym.symTab);
        /*String name = "this";
        contractSym.addVar(name, contractSym.toVarSym(name,
                new LabeledType(contractName, new PrimitiveIfLabel(new Name("this"))), true, false,
                null, scopeContext));

         */

        // Utils.addBuiltInSymsIfc(contractSym);

        for (StructDef def: structDefs) {
            def.globalInfoVisit(contractSym);
        }
        for (StateVariableDeclaration dec : varDeclarations) {
            dec.globalInfoVisit(contractSym);
        }

        trustSetting.globalInfoVisit(contractSym);

        for (ExceptionDef expDef : exceptionDefs) {
            expDef.globalInfoVisit(contractSym);
        }

        for (EventDef eventDef : eventDefs) {
            eventDef.globalInfoVisit(contractSym);
        }

        for (FunctionDef fDef : methodDeclarations) {
            fDef.globalInfoVisit(contractSym);
        }
    }

    public void IFCVisit(VisitEnv env, boolean tail_position) throws SemanticException {
        //env.prevContext = new Context()
        // findPrincipal(env.principalSet);

        for (StateVariableDeclaration dec : varDeclarations) {
            dec.IFCVisit(env, tail_position);
        }
        trustSetting.IFCVisit(env, tail_position);

        for (ExceptionDef expDef : exceptionDefs) {
            expDef.IFCVisit(env, tail_position);
        }

        for (EventDef eventDef : eventDefs) {
            eventDef.IFCVisit(env, tail_position);
        }

        for (FunctionDef fDef : methodDeclarations) {
            // System.err.println("Typechecking " + fDef.name + "@" + contractName + ": " + fDef.isNative);
            fDef.IFCVisit(env, tail_position);
        }
    }

//    public void findPrincipal(HashSet<String> principalSet) {
//        for (TrustConstraint trustConstraint : trustSetting.trust_list) {
//            trustConstraint.findPrincipal(principalSet);
//        }
//        ifl.findPrincipal(principalSet);
//
//        for (StateVariableDeclaration dec : varDeclarations) {
//            dec.findPrincipal(principalSet);
//        }
//
//        for (ExceptionDef expDef : exceptionDefs) {
//            expDef.findPrincipal(principalSet);
//        }
//
//        for (FunctionDef fDef : methodDeclarations) {
//            fDef.findPrincipal(principalSet);
//        }
//    }

    public compile.ast.Contract solidityCodeGen(CompileEnv code) {

        code.setExceptionMap(exceptionTypeSymMap);
        code.setEventMap(eventTypeSymMap);
        List<VarDec> stateVarDecs = new ArrayList<>();
        List<compile.ast.StructDef> structAndExcDefs = new ArrayList<>();
        List<compile.ast.Event> evDefs = new ArrayList<>();
        List<Function> methods = new ArrayList<>();
        for (StructDef structDef: structDefs) {
            structAndExcDefs.add(structDef.solidityCodeGen(code));
        }

        for (StateVariableDeclaration dec : varDeclarations)
            if (!dec.isBuiltIn()) {
                stateVarDecs.add(dec.solidityCodeGen(code));
            }

        for (ExceptionDef exceptionDef: exceptionDefs) {
            if (!exceptionDef.arguments.empty()) {
                structAndExcDefs.add(exceptionDef.solidityCodeGen(code));
            }
        }

        for (EventDef eventDef: eventDefs) {
            evDefs.add(eventDef.solidityCodeGen(code));
        }

        for (FunctionDef fDef : methodDeclarations)
            if (!fDef.isBuiltIn()) {
                methods.add(fDef.solidityCodeGen(code));
            }

        methods.addAll(code.tempFunctions());
        code.clearTempFunctions();
        return new compile.ast.Contract(contractName, implementsContractName, stateVarDecs, structAndExcDefs, evDefs, methods);
    }

    @Override
    public void passScopeContext(ScopeContext parent) {
        scopeContext = new ScopeContext(this, parent);
        for (Node node : children()) {
            node.passScopeContext(scopeContext);
        }
    }

    @Override
    public ArrayList<Node> children() {
        ArrayList<Node> rtn = new ArrayList<>();
        rtn.addAll(trustSetting.trust_list);
        rtn.addAll(structDefs);
        rtn.addAll(varDeclarations);
        rtn.addAll(exceptionDefs);
        rtn.addAll(eventDefs);
        rtn.addAll(methodDeclarations);
        return rtn;
    }

    // TODO(steph): fix asserts
    void checkInterfaceSignature(String implementsContractName, Map<String, Interface> interfaceMap) {
        Interface itrface = interfaceMap.get(implementsContractName);
        assert itrface != null : "interface contract not found: " + implementsContractName + " in " + contractName;
        // check that all methods are implemented
        // check that there are no duplicates
        Set<String> nameSet = new HashSet<>();
        for (StructDef structDef: structDefs) {
            nameSet.add(structDef.structName);
        }
        for (ExceptionDef exp: exceptionDefs) {
            nameSet.add(exp.exceptionName);
        }
        for (EventDef eventDef: eventDefs) {
            nameSet.add(eventDef.eventName);
        }
        for (FunctionSig f: methodDeclarations) {
            nameSet.add(f.name);
        }

        Map<String, StructDef> structMap = new HashMap<>();
        for (StructDef f : structDefs) {
            structMap.put(f.structName, f);
        }
        for (StructDef structDef: itrface.structDefs) {
            if (structDef.isBuiltIn()) continue;
            if (nameSet.contains(structDef.structName)) {
                if (!structDef.typeMatch(structMap.get(structDef.structName))) {
                    assert false : structDef.structName;
                }
            }
        }

        Map<String, ExceptionDef> expMap = new HashMap<>();
        for (ExceptionDef f : exceptionDefs) {
            expMap.put(f.exceptionName, f);
        }
        for (ExceptionDef exp: itrface.exceptionDefs) {
            if (exp.isBuiltIn()) continue;
            if (nameSet.contains(exp.exceptionName)) {
                if (!exp.typeMatch(expMap.get(exp.exceptionName))) {
                    assert false : exp.exceptionName;
                }
            }
        }

        Map<String, EventDef> eventMap = new HashMap<>();
        for (EventDef f : eventDefs) {
            eventMap.put(f.eventName, f);
        }
        for (EventDef eventDef : itrface.eventDefs) {
            if (eventDef.isBuiltIn()) continue;
            if (nameSet.contains(eventDef.eventName)) {
                if (!eventDef.typeMatch(eventMap.get(eventDef.eventName))) {
                    assert false : eventDef.eventName;
                }
            }
        }

        Map<String, FunctionSig> funcMap = new HashMap<>();
        for (FunctionDef f : methodDeclarations) {
            funcMap.put(f.name, f);
        }
        for (FunctionSig f : itrface.funcSigs) {
            String name = f.name;
            if (funcMap.containsKey(name)) {
                if (!f.typeMatch(funcMap.get(name))) {
                    assert false :
                            contractName + ": implemented method carries unmatched types: "
                                    + f.signature() + " $ " + funcMap.get(name).signature();
                }
            } else {
                assert false : name + " is not implemented in " + contractName;
            }
        }

    }

    public void codePasteContract(Map<String, Contract> contractMap, Map<String, Interface> interfaceMap) throws SemanticException {
        if (!implementsContractName.isEmpty()) {
            // check against the super interface
            checkInterfaceSignature(implementsContractName, interfaceMap);
        }

        if (!extendsContractName.isEmpty()) {
//            System.err.println("pasting code from " + extendsContractName + " to " + contractName);
            // check no functions with the same name
            // add other functions from superContract
            // trust_list is also inherited
            Contract superContract = contractMap.get(extendsContractName);
            assert superContract != null : "super contrasct not found: " + extendsContractName + " in " + contractName;

            // inherit from superContract

            // trust_list
            List<TrustConstraint> newTrustCons = new ArrayList<>();
            newTrustCons.addAll(superContract.trustSetting.trust_list);
            newTrustCons.addAll(trustSetting.trust_list);
            trustSetting.trust_list = newTrustCons;

            // Statement
            Map<String, StateVariableDeclaration> varNames = new HashMap<>();
//            Map<String, StructDef> strDefs = new HashMap<>();
            Map<String, ExceptionDef> expDefs = new HashMap<>();
            Map<String, EventDef> evDefs = new HashMap<>();
            Map<String, FunctionSig> funcNames = new HashMap<>();
            Set<String> nameSet = new HashSet<>();
            for (StructDef structDef: structDefs) {
                assert !nameSet.contains(structDef.structName) : "duplicate name: " + structDef.structName;
//                strDefs.put(structDef.structName, structDef);
                nameSet.add(structDef.structName);
            }

            for (StateVariableDeclaration a : varDeclarations) {
                Name x = a.name();
                if (nameSet.contains(x.id))
                    throw new SemanticException("duplicate variable: " + x.id, a.location);
                varNames.put(x.id, a);
                nameSet.add(x.id);
            }

            for (ExceptionDef exp : exceptionDefs) {
                if (nameSet.contains(exp.exceptionName)) {
                    throw new SemanticException("duplicate exception: " + exp.exceptionName, exp.location);
                }
                nameSet.add(exp.exceptionName);
                expDefs.put(exp.exceptionName, exp);
            }

            for (EventDef eventDef : eventDefs) {
                if (nameSet.contains(eventDef.eventName)) {
                    throw new SemanticException("duplicate event: " + eventDef.eventName, eventDef.location);
                }
                nameSet.add(eventDef.eventName);
                evDefs.put(eventDef.eventName, eventDef);
            }

            for (FunctionDef f : methodDeclarations) {
                if (nameSet.contains(f.name)) {
                    throw new SemanticException("duplicate method: " + f.name, f.location);
                }
                nameSet.add(f.name);
                funcNames.put(f.name, f);
            }

            List<StateVariableDeclaration> newStateVarDecs = new ArrayList<>();
            List<StructDef> newStructDefs = new ArrayList<>();
            List<ExceptionDef> newExpDefs = new ArrayList<>();
            List<EventDef> newEvDefs = new ArrayList<>();
            List<FunctionDef> newFuncDefs = new ArrayList<>();

            int builtInIndex = 0;
            for (StructDef a: structDefs) {
                if (a.isBuiltIn()) {
                    newStructDefs.add(a);
                } else
                    break;
                builtInIndex += 1;
            }
            for (StructDef a: superContract.structDefs) {
                if (a.isBuiltIn()) {
                    continue;
                }
                assert (!nameSet.contains(a.structName)) : a.structName;
                newStructDefs.add(a);
            }
            newStructDefs.addAll(structDefs.subList(builtInIndex, structDefs.size()));


            builtInIndex = 0;
            for (StateVariableDeclaration a : varDeclarations) {
                if (a.isBuiltIn())
                    newStateVarDecs.add(a);
                else
                    break;
                builtInIndex += 1;
            }
            for (StateVariableDeclaration a : superContract.varDeclarations) {
                if (a.isBuiltIn())
                    continue;
                Name x = a.name();
                assert !nameSet.contains(x.id) : x.id;

                newStateVarDecs.add(a);
            }
            newStateVarDecs.addAll(varDeclarations.subList(builtInIndex, varDeclarations.size()));

            builtInIndex = 0;
            for (ExceptionDef a : exceptionDefs) {
                if (a.isBuiltIn())
                    newExpDefs.add(a);
                else
                    break;
                builtInIndex += 1;
            }
            for (ExceptionDef exp : superContract.exceptionDefs) {
                if (exp.isBuiltIn())
                    continue;
                if (nameSet.contains(exp.exceptionName)) {
                    throw new SemanticException("duplicate exception: " + exp.exceptionName, exp.location);
                }
                newExpDefs.add(exp);
            }
            newExpDefs.addAll(exceptionDefs.subList(builtInIndex, exceptionDefs.size()));

            builtInIndex = 0;
            for (EventDef a : eventDefs) {
                if (a.isBuiltIn())
                    newEvDefs.add(a);
                else
                    break;
                builtInIndex += 1;
            }
            for (EventDef eventDef : superContract.eventDefs) {
                if (eventDef.isBuiltIn())
                    continue;
                if (nameSet.contains(eventDef.eventName)) {
                    throw new SemanticException("duplicate event: " + eventDef.eventName, eventDef.location);
                }
                newEvDefs.add(eventDef);
            }
            newEvDefs.addAll(eventDefs.subList(builtInIndex, eventDefs.size()));

            builtInIndex = 0;
            for (FunctionDef a : methodDeclarations) {
                if (a.isBuiltIn())
                    newFuncDefs.add(a);
                else
                    break;
                builtInIndex += 1;
            }
            for (FunctionDef f : superContract.methodDeclarations) {
//                System.err.println("pasting " + f.name + " " + f.isConstructor());
                if (f.isBuiltIn())
                    continue;
                if (f.isConstructor()) {
                    // rename
                    FunctionDef newF = new FunctionDef(f, f.body);
                    newF.changeName(Utils.genSuperName(contractName));
                    newF.makeNonConstructor();
                    newF.renameSuperCall(extendsContractName);
                    newFuncDefs.add(newF);
//                    System.err.println("renaming super constructor to " + Utils.genSuperName(contractName));
                    continue;
                }
                boolean overridden = false;
                if (funcNames.containsKey(f.name)) {
                    assert funcNames.get(f.name).typeMatch(f);
                    overridden = true;
                } else if (varNames.containsKey(f.name)) {
                    // TODO: var overridden by func
                    assert false;
                } else
                    assert !expDefs.containsKey(f.name);

                if (!overridden) {
                    newFuncDefs.add(f);
                }
            }
            newFuncDefs.addAll(methodDeclarations.subList(builtInIndex, methodDeclarations.size()));

            structDefs = newStructDefs;
            varDeclarations = newStateVarDecs;
            exceptionDefs = newExpDefs;
            eventDefs = newEvDefs;
            methodDeclarations = newFuncDefs;
        }
    }

    public String toString() {
        return toSHErrLocFmt();
        //return genson.serialize(body.get();
    }

    public String getContractName() {
        return contractName;
    }

    protected void addBuiltIns() {
        addBuiltInTrustSettings();
        addBuiltInVars();
        addBuiltInExceptions();
        addBuiltInEvents();
        addBuiltInMethods();
    }

    private void addBuiltInMethods() {
        // add methods:
        final IfLabel labelThis = new PrimitiveIfLabel(new Name(Utils.LABEL_THIS));
        final IfLabel labelTop = new PrimitiveIfLabel(new Name(Utils.LABEL_TOP));
        final IfLabel labelBot = new PrimitiveIfLabel(new Name(Utils.LABEL_BOTTOM));
        labelThis.setLoc(CodeLocation.builtinCodeLocation());
        labelTop.setLoc(CodeLocation.builtinCodeLocation());
        labelBot.setLoc(CodeLocation.builtinCodeLocation());

        // @protected
        // @final
        // void send{this -> TOP; BOT}(address target, uint amount);
        List<Arg> args = new ArrayList<>();
        Arg tmparg = new Arg(
                "target",
                new LabeledType(Utils.ADDRESS_TYPE, labelThis, CodeLocation.builtinCodeLocation()),
                false,
                true
        );
        args.add(tmparg);
        tmparg = new Arg(
                "amount",
                new LabeledType(Utils.BuiltinType2ID(BuiltInT.UINT), labelThis, CodeLocation.builtinCodeLocation()),
                false,
                true
        );
        args.add(tmparg);
        int count = 0;
        for (Arg arg : args) {
            CodeLocation location = new CodeLocation(1, count, "Builtin");
            arg.setLoc(location);
            arg.annotation.setLoc(location);
            arg.annotation.type().setLoc(location);
            ++count;
        }
        List<String> decs = new ArrayList<>();
        decs.add(Utils.PROTECTED_DECORATOR);
        decs.add(Utils.FINAL_DECORATOR);
        FunctionDef sendDef = new FunctionDef(
                Utils.METHOD_SEND_NAME,
                new FuncLabels(
                        labelThis,
                        labelTop,
                        labelBot,
                        labelBot
                ),
                new Arguments(args),
                new ArrayList<>(),
                decs,
                Utils.builtinLabeldType(BuiltInT.VOID),
                false,
                true,
                CodeLocation.builtinCodeLocation()
        );
        methodDeclarations.add(sendDef);

        // @public
        // final
        // uint{TOP} balance{BOT -> TOP; TOP}(final address addr);
        args = new ArrayList<>();
        args.add(new Arg(
                "addr",
                new LabeledType(Utils.ADDRESS_TYPE, new PrimitiveIfLabel(new Name(Utils.LABEL_BOTTOM)), CodeLocation.builtinCodeLocation()),
                false,
                true
        ));
        count = 0;
        for (Arg arg : args) {
            CodeLocation location = new CodeLocation(1, count, "Builtin");
            arg.setLoc(location);
            arg.annotation.setLoc(location);
            arg.annotation.type().setLoc(location);
            ++count;
        }
        decs = new ArrayList<>();
        decs.add(Utils.PUBLIC_DECORATOR);
        decs.add(Utils.FINAL_DECORATOR);
        FunctionDef balanceDef = new FunctionDef(
                Utils.METHOD_BALANCE_NAME,
                new FuncLabels(
                        labelBot,
                        labelBot,
                        labelTop,
                        labelTop
                ),
                new Arguments(args),
                new ArrayList<>(),
                decs,
                new LabeledType(Utils.BuiltinType2ID(BuiltInT.UINT), labelTop, CodeLocation.builtinCodeLocation()),
                false,
                true,
                CodeLocation.builtinCodeLocation()
        );
        methodDeclarations.add(balanceDef);
    }

    private void addBuiltInExceptions() {
        // add exceptions:
        // exception{BOT} Error();
//        ExceptionDef error = new ExceptionDef(
//                Utils.EXCEPTION_ERROR_NAME,
//                new Arguments(),
//                true
//        );
//        exceptionDefs.add(error);
    }

    private void addBuiltInEvents() {
        // No builtin events
    }

    private void addBuiltInVars() {
        // add variables:
        // final This{this} this;
        StateVariableDeclaration thisDec = new StateVariableDeclaration(
                new Name(Utils.LABEL_THIS),
                new LabeledType(contractName, new PrimitiveIfLabel(new Name(Utils.LABEL_THIS)), CodeLocation.builtinCodeLocation(0, 1)),
                null,
                true,
                true,
                false,
                true
        );
        thisDec.name().setLoc(CodeLocation.builtinCodeLocation(0, 0));
        thisDec.setLoc(CodeLocation.builtinCodeLocation(0, 0));
        StateVariableDeclaration topDec = new StateVariableDeclaration(
                new Name(Utils.LABEL_TOP),
                new LabeledType(Utils.ADDRESS_TYPE, new PrimitiveIfLabel(new Name(Utils.LABEL_TOP)), CodeLocation.builtinCodeLocation(1, 1)),
                null,
                true,
                true,
                false,
                true
        );
        topDec.name().setLoc(CodeLocation.builtinCodeLocation(1, 0));
        topDec.setLoc(CodeLocation.builtinCodeLocation(1, 0));
        StateVariableDeclaration botDec = new StateVariableDeclaration(
                new Name(Utils.LABEL_BOTTOM),
                new LabeledType(Utils.ADDRESS_TYPE, new PrimitiveIfLabel(new Name(Utils.LABEL_BOTTOM)), CodeLocation.builtinCodeLocation(2, 1)),
                null,
                true,
                true,
                false,
                true
        );
        botDec.name().setLoc(CodeLocation.builtinCodeLocation(2, 0));
        botDec.setLoc(CodeLocation.builtinCodeLocation(2, 0));
        StateVariableDeclaration uintmaxDec = new StateVariableDeclaration(
                new Name(Utils.UINTMAX),
                new LabeledType(Utils.UINT_TYPE, new PrimitiveIfLabel(new Name(Utils.LABEL_TOP)), CodeLocation.builtinCodeLocation(3, 1)),
                null,
                true,
                true,
                false,
                true
        );
        uintmaxDec.name().setLoc(CodeLocation.builtinCodeLocation(3, 0));
        uintmaxDec.setLoc(CodeLocation.builtinCodeLocation(3, 0));
        List<StateVariableDeclaration> newDecs = new ArrayList<>();
        newDecs.add(topDec);
        newDecs.add(botDec);
        newDecs.add(thisDec);
        newDecs.add(uintmaxDec);
        newDecs.addAll(varDeclarations);
        varDeclarations = newDecs;
    }

    private void addBuiltInTrustSettings() {
        trustSetting.addBuiltIns();
    }


    public void clearExtends() {
        extendsContractName = "";
    }
}