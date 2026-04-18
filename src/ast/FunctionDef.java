package ast;

import compile.CompileEnv;
import compile.CompileEnv.ScopeType;
import compile.ast.Argument;
import compile.ast.Call;
import compile.ast.Constructor;
import compile.ast.Function;
import compile.ast.Return;
import compile.ast.SingleVar;
import compile.ast.Type;
import compile.ast.VarDec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Inequality;
import typecheck.sherrlocUtils.Relation;
import typecheck.*;

import java.util.HashMap;
import java.util.Map;

public class FunctionDef extends FunctionSig {

    private static boolean DEBUG = false;

    List<Statement> body;

    public FunctionDef(String name, FuncLabels funcLabels, Arguments args,
            List<Statement> body, List<String> decoratorList, LabeledType rtn, boolean isConstructor,
            CodeLocation location) {
        super(name, funcLabels, args, decoratorList, rtn, isConstructor, location);
        this.body = body;
    }
    public FunctionDef(String name, FuncLabels funcLabels, Arguments args,
            List<Statement> body, List<String> decoratorList, LabeledType rtn, boolean isConstructor, boolean isBuiltIn,
            CodeLocation location) {
        super(name, funcLabels, args, decoratorList, rtn, isConstructor, isBuiltIn, location);
        this.body = body;
    }

    public FunctionDef(FunctionSig funcSig, List<Statement> body) {
        super(funcSig);
        this.body = body;
    }


    @Override
    public ScopeContext genTypeConstraints(NTCEnv env, ScopeContext parent) throws SemanticException {
//        if (isSuperConstructor()) return null;
        //env.setCurSymTab(new SymTab(env.curSymTab()));
        env.enterNewScope();
        // add args to local sym;
        String funcName = this.name;
        if (DEBUG) System.err.println("entering method: " + name + " " + body.size());
        FuncSym funcSym = ((FuncSym) env.getCurSym(funcName));
        Map<ExceptionTypeSym, Boolean> exceptionTypeSyms = new HashMap<>();
        for (Map.Entry<ExceptionTypeSym, String> t : funcSym.exceptions.entrySet()) {
            exceptionTypeSyms.put(t.getKey(), true);
        }

        ScopeContext now = new ScopeContext(this, parent, exceptionTypeSyms);
        scopeContext = now;
        passScopeContext();

        // add built-in vars
        addBuiltInVars(env.curSymTab(), now);

        if (!returnVoid()) addBuiltInResult(env.curSymTab(), now);

        for (Arg arg : this.args.args()) {
            arg.genTypeConstraints(env, now);
        }
        funcLabels.genTypeConstraints(env, now);
        if (funcSym.returnType != null) {
            env.addCons(new Constraint(new Inequality(funcSym.returnTypeSLC(), Relation.EQ,
                    funcSym.returnType.toSHErrLocFmt()), env.globalHypothesis(), location,
                    "Label of this method's return value"));
        }

//        env.enterMethod(getName());
        if (isConstructor()) {
            env.enterConstructor();
//            System.err.println("containing statments: " + name + " " + body.size() + " from " + env.curContractSym().getName());
        }
        //if (!isBuiltIn()) {
            // TODO: add support for signatures
            for (Statement stmt : body) {
                // // logger.debug("stmt: " + stmt);
                stmt.genTypeConstraints(env, now);
            }
        if (isConstructor()) {
            // TODO(steph): fix assert
            assert env.superCalled() : "constructor of super contract is not called in the constructor of " + env.currentSourceFileFullName();
            env.leaveConstructor();
        }
        //}
        //env.setCurSymTab(env.curSymTab().getParent());
//        env.exitMethod();
        env.exitNewScope();
        return now;
    }


    @Override
    public PathOutcome IFCVisit(VisitEnv env, boolean tail_info) throws SemanticException {
        if (isBuiltIn() || isNative() || isSuperConstructor()) return null;
        env.incScopeLayer();
        env.enterNewMethod(getName());
        addBuiltInVars(env.curSymTab, scopeContext);
        if (!returnVoid()) {
            addBuiltInResult(env.curSymTab, scopeContext);
        }

        for (Entry<String, VarSym> entry: env.curSymTab.getVars().entrySet()) {
            VarSym varSym = entry.getValue();
            if (varSym.isFinal && (varSym.typeSym instanceof ContractSym || varSym.typeSym.getName().equals(Utils.ADDRESS_TYPE))) {
                env.addPrincipal(varSym);
            }
        }

        String funcLocalName = name;

        String ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
        FuncSym funcSym = env.getFunc(funcLocalName);
        env.setCurFuncSym(funcSym);
        String funcFullName = funcSym.toSHErrLocFmt();
        // Context curContext = new Context(ifNamePc, Utils.getLabelNameFuncRtnLock(funcName), Utils.getLabelNameInLock(location));
        String inLockName = Utils.getLabelNameInLock(funcFullName);
        String outLockName = Utils.getLabelNameFuncRtnLock(funcFullName);
        String outPcName = Utils.getLabelNameFuncRtnPc(funcFullName);
        Context curContext = new Context(ifNamePc, inLockName);

        String ifNameCall = funcSym.internalPcSLC();
        env.addTrustConstraint(
                new Constraint(new Inequality(ifNameCall, Relation.EQ, ifNamePc), env.hypothesis(),
                        funcLabels.to_pc.location,
                        "Control flow of this method start with its call-after(second) label"));

//        String ifNameContract = env.curContractSym().getLabelNameContract();
//        env.addTrustConstraint(new Constraint(new Inequality(ifNameContract, ifNameCall), env.hypothesis(),
//                funcLabels.begin_pc.location,
//                "This contract should be trusted enough to call this method"));

        String ifNameGamma = funcSym.getLabelNameCallGamma();
        env.addTrustConstraint(new Constraint(new Inequality(inLockName, ifNamePc), env.hypothesis(),
                funcLabels.to_pc.location,
                "The statically locked integrity must be at least as trusted as initial pc integrity"));
        env.cons.add(
                new Constraint(new Inequality(Utils.joinLabels(inLockName, outLockName), ifNameGamma),
                        env.hypothesis(), funcLabels.gamma_label.location,
                        "This function does not maintain reentrancy locks as specified in signature"));

        HashMap<ExceptionTypeSym, PsiUnit> psi = new HashMap<>();
        for (Map.Entry<ExceptionTypeSym, String> exp : funcSym.exceptions.entrySet()) {
            psi.put(exp.getKey(), new PsiUnit(
                    //new Context(funcSym.getLabelNameException(exp.getKey()), ifNameGamma), true));
                    new Context(exp.getKey().labelNameSLC(), ifNameGamma), true));
        }

        Context funcBeginContext = curContext;
        PsiUnit funcEndContext = new PsiUnit(outPcName, outLockName);
        // env.inContext = funcBeginContext;
        // env.outContext = funcEndContext;

        args.genConsVisit(env, false);
        // Context prev = new Context(env.prevContext);//, prev2 = null;
        CodeLocation loc = null;
        PathOutcome CO = null;
        env.inContext = funcBeginContext;
//        Utils.genConsStatments(body, env, CO, true);
        int index = 0;
        for (Statement stmt : body) {
            // Context CO = new Context(Utils.getLabelNamePc(stmt.location), Utils.getLabelNameLock(stmt.location));
            // env.outContext = CO;
            ++index;
            String prevLambda = env.inContext.lambda;
            boolean isTail = index == body.size() && tail_info;
            CO = stmt.IFCVisit(env, isTail);
            //Context CO = env.outContext;
            if (CO.existsNormalPath()) {
                env.inContext = Utils.genNewContextAndConstraints(env, isTail, CO.getNormalPath().c, prevLambda, stmt.nextPcSHL(), stmt.location());

//                env.inContext = new Context(CO.getNormalPath().c());
            } else {
                break;
            }
        }
        if (body.size() > 0 && CO.existsNormalPath()) {
            if (DEBUG) System.err.println("method: " + funcLocalName);
            Utils.contextFlow(env, CO.getNormalPath().c(), funcEndContext.c(),
                    body.get(body.size() - 1).location);
        }

        env.decScopeLayer();
        env.exitMethod();

        return null;
    }

    private boolean isSuperConstructor() {
        return Utils.isSuperConstructor(name);
    }
    /*public void findPrincipal(HashSet<String> principalSet) {
        if (sig.name instanceof LabeledType) {
            ((LabeledType) sig.name).ifl.findPrincipal(principalSet);
        }
        sig.args.findPrincipal(principalSet);

        if (sig.rnt instanceof LabeledType) {
            ((LabeledType) sig.rnt).ifl.findPrincipal(principalSet);
        }
    }*/

    @Override
    public Function solidityCodeGen(CompileEnv code) {
        code.setCurrentMethod(this, returnSolType(code));
        code.enterNewVarScope();
        code.pushScope(ScopeType.METHOD);
        code.clearExceptionManager();
        boolean pub = false;
        boolean payable = false;
        if (decoratorList != null) {
            if (decoratorList.contains(Utils.PUBLIC_DECORATOR)) {
                pub = true;
            }
            if (decoratorList.contains(Utils.PAYABLE_DECORATOR)) {
                payable = true;
            }
        }

        if (isConstructor()) {
            List<Argument> arguments = args.solidityCodeGen(code);
            List<compile.ast.Statement> statements = new ArrayList<>();
            compile.Utils.addBuiltInVars(true, statements, code);
            for (Statement s: body) {
                statements.addAll(s.solidityCodeGen(code));
            }
            code.exitVarScope();
            code.popScope();
            return new Constructor(arguments, statements);
        } else {
            // create two functions:
            //  a private function which implements the real deal and
            //  a public function whose name is hashed and call the private function
            String methodName = isPublic ? Utils.methodNameHash(name, this) : name;
            List<compile.ast.Statement> statements = new ArrayList<>();
            List<compile.ast.Statement> wapperStatements = new ArrayList<>();
            compile.Utils.addBuiltInVars(isPublic, statements, code);
            if (isPublic) {
                compile.Utils.addBuiltInVars(isPublic, wapperStatements, code);
            }
            List<Argument> arguments = args.solidityCodeGen(code);
            Type returnType, originalReturnType = returnSolType(code);
            if (exceptionFree()) {
                returnType = originalReturnType;
            } else {
                if (!returnVoid()) {
                    statements.add(new VarDec(originalReturnType, compile.Utils.RESULT_VAR_NAME));
                }
                returnType = compile.Utils.UNIVERSAL_RETURN_TYPE;
                for (LabeledType iftype: exceptionList) {
                    code.getExceptionId(code.findExceptionTypeSym(iftype.type().name));
                }
            }
                    // = Function.builtInVarDefs(exceptionHandlingFree(), originalReturnType);
            /*
                f{pc}(x_i{l_i}) from sender
                assert sender => pc, l_i
             */
            if (pub && !isNative()) {
                wapperStatements.addAll(code.enterFuncCheck(funcLabels, args));
//                statements.addAll(code.enterFuncCheck(funcLabels, args));
            }
            for (Statement s: body) {
                List<compile.ast.Statement> tmp = s.solidityCodeGen(code);
                statements.addAll(tmp);
            }

            if (!exceptionFree()) {
                statements.addAll(code.compileReturn(rtn.type().isVoid() ? null : new SingleVar(
                        compile.Utils.RESULT_VAR_NAME)));
            }
            code.exitVarScope();
            code.popScope();
            if (isPublic) {
                code.addTemporaryFunction(new Function(name, arguments, returnType, false, false, statements));
                wapperStatements.add(new Return(new Call(name, arguments.stream().map(arg -> new SingleVar(arg.name())).collect(
                        Collectors.toList()))));
                return new Function(methodName, arguments, returnType, pub, payable, wapperStatements);
            } else {
                return new Function(methodName, arguments, returnType, pub, payable, statements);
            }
        }
    }

    private boolean exceptionHandlingFree() {
        if (!exceptionFree()) return false;
        for (Statement s: body) {
            if (!s.exceptionHandlingFree()) return false;
        }
        return true;
    }

    @Override
    public List<Node> children() {
        List<Node> rtn = super.children();
        rtn.addAll(body);
        return rtn;
    }

    public void renameSuperCall(String extendsContractName) {
        List<Statement> newBody = new ArrayList<>();
        for (Statement s: body) {
            if (s instanceof ast.CallStatement callStatement) {
                CallStatement newS = new CallStatement(callStatement);
                newS.call.checkAndRenameSuper(extendsContractName);
                newBody.add(new CallStatement(new ast.Call()));
            } else {
                newBody.add(s);
            }
        }
    }

    public Type returnSolType(CompileEnv code) {
        return rtn.type().solidityCodeGen(code);
    }
//    @Override
//    public String toSHErrLocFmt() {
//        return this.getClass().getSimpleName() + "." + name() + "." + location;
//    }
}
