package ast;

import compile.CompileEnv;
import compile.ast.Assign;
import compile.ast.Attr;
import compile.ast.BinaryExpression;
import compile.ast.ExternalCall;
import compile.ast.IfStatement;
import compile.ast.Literal;
import compile.ast.Pass;
import compile.ast.PrimitiveType;
import compile.ast.Return;
import compile.ast.SingleVar;
import compile.ast.Statement;
import compile.ast.Type;
import compile.ast.VarDec;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Inequality;
import typecheck.sherrlocUtils.Relation;
import typecheck.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Call extends TrailerExpr {

    List<Expression> args;


    // store the called func symbol after regular typechecking
    FuncSym funcSym = null;
    private boolean isCast;
    boolean builtIn = false, ntced = false;
    CallSpec callSpec;

    public Call() {
        this.args = new ArrayList<>();
    }

    public Call(Call call) {
        value = call.value;
        args = call.args;
        if (value instanceof Name) {
            value = new Name(((Name) value).id);
        }
    }

    public Call(Expression x, List<Expression> ys) {
        value = x;
        args = ys;
    }

    public void addArg(Expression arg) {
        this.args.add(arg);
    }

    private void setArgs(List<Expression> args) {
        this.args = args;
    }

    public void setSpec(CallSpec callSpec) {
        this.callSpec = callSpec;
    }

    public Expression getArgAt(int index) {
        return args.get(index);
    }

    public ScopeContext genTypeConstraints(NTCEnv env, ScopeContext parent) throws SemanticException {
        this.ntced = true;
        ScopeContext now = new ScopeContext(this, parent);
        String funcName;
        FuncSym funcSym;
        boolean extern = false;
        if (!(value instanceof Name)) {
            if (value instanceof Attribute) {
                // a.b(c), a must be a contract or an array
                extern = true;
                Attribute att = (Attribute) value;
                assert att.value instanceof Name : "at " + location.errString();
                String varName = ((Name) att.value).id;
                // System.out.println(varName);
                funcName = att.attr.id;
                Sym s = env.getCurSym(varName);
                assert s != null: "variable not found: " + varName + " at " + location.errString();
                // logger.debug("var " + varName + ": " + s.getName());
                if (s instanceof VarSym varSym) {
                    if (varSym.typeSym instanceof InterfaceSym contractSym) {
                        s = contractSym.getFunc(funcName);
                        if (s == null)
                            throw new SemanticException("function " + varName + "." + funcName + "() not found",
                                location);
                        funcSym = (FuncSym) s;

                    } else if (varSym.typeSym instanceof ArrayTypeSym arrayTypeSym) {
                        // TODO: change the hard-code style
                        // TODO: factor this block out into its own method
                        TypeSym arrayTSym = arrayTypeSym.valueType;
                        String arrayTName = arrayTSym.toSHErrLocFmt();
                        this.builtIn = true;
                        if (funcName.equals("pop")) {
                            // return T
                            if (!args.isEmpty()) throw new SemanticException("pop expects 0 arguments",
                                    this.location);
                            env.addCons(now.genTypeConstraints(arrayTName, Relation.EQ, env, location));
                            return now;
                        } else if (funcName.equals("push")) {
                            // require one T, return void
                            if (args.size() != 1) throw new SemanticException("push expects 1 argument",
                                    this.location);
                            Expression arg = args.get(0);
                            ScopeContext argContext = arg.genTypeConstraints(env, now);
                            env.addCons(argContext.genTypeConstraints(arrayTName, Relation.GEQ, env, arg.location));
                            TypeSym rtnTypeSym = (TypeSym) env.getSym(BuiltInT.VOID);
                            env.addCons(now.genTypeConstraints(rtnTypeSym.toSHErrLocFmt(), Relation.EQ, env, location));
                            return now;
                        } else if (funcName.equals("length")) {
                            // return uint
                            if (!args.isEmpty()) {
                                throw new SemanticException("length expects 0 arguments",
                                        this.location);
                            }
                            TypeSym rtnTypeSym = (TypeSym) env.getSym(BuiltInT.UINT);
                            env.addCons(now.genTypeConstraints(rtnTypeSym.toSHErrLocFmt(), Relation.EQ, env, location));
                            return now;
                        } else {
                            throw new SemanticException("type error: unknown array operator", this.location);
                        }
                    } else {
                        throw new SemanticException("type error: " + varName + "." + funcName + "() " + varSym.typeSym.toSHErrLocFmt() + (varSym.typeSym instanceof ContractSym),
                            this.location);
                    }
                } else {
                    throw new SemanticException("type error in call (not an attribute): ", this.location);
                }
            } else {
                throw new SemanticException("type error in call (not a name): ", this.location);
            }
        } else {
            // a(b) - internal contract call
            funcName = ((Name) value).id;
            // System.out.println(funcName);
            Sym s;
            if (funcName.equals(Utils.SUPER_KEYWORD)) {
                if (env.superCalled())
                    throw new SemanticException("cannot call super() twice in the constructor: ", location);
                if (!env.inConstructor())
                    throw new SemanticException("cannot call super() outside the constructor: " , location);
                String newFuncName = Utils.genSuperName(env.curContractSym().getName());
                s = env.getCurSym(newFuncName);
                if (s == null) {
                    throw new SemanticException("could not find superclass constructor",
                            location);
                }
                // TODO: think twice
                 ((Name) value).id = newFuncName;
                env.callSuper();
            } else {
                s = env.getCurSym(funcName);
            }
            if (s == null) {
                throw new SemanticException("method not found: " + funcName,
                    location);
            }
            if (!(s instanceof FuncSym)) {
                if (s instanceof InterfaceSym || s instanceof BuiltinTypeSym) {
                    env.addCons(now.genTypeConstraints(s.getName(), Relation.EQ, env, location));
                    isCast = true;
                    return now;
                }
                throw new SemanticException("contract not found: " + s.getName(),
                    location);
            }
            funcSym = ((FuncSym) s);
            if (funcSym.isBuiltIn()) {
                this.builtIn = true;
            }
        }
        if (env.inConstructor() && !env.superCalled())
            throw new SemanticException("cannot call methods before super called in constructor: " + funcName,
                 location);
        if (args.size() != funcSym.parameters.size())
            throw new SemanticException("number of arguments does not match the number of parameters of the called method: " + funcName,
                location);
        this.funcSym = funcSym;

        if (extern && callSpec != null) {
            callSpec.genTypeConstraints(env, now);
        }
        // typecheck arguments
        for (int i = 0; i < args.size(); ++i) {
            Expression arg = args.get(i);
            TypeSym paraInfo = funcSym.parameters.get(i).typeSym;
            ScopeContext argContext = arg.genTypeConstraints(env, now);
            String typeName = paraInfo.toSHErrLocFmt();
            env.addCons(argContext.genTypeConstraints(typeName, Relation.GEQ, env, arg.location));
        }
        String rtnTypeName = funcSym.returnType.toSHErrLocFmt();
        env.addCons(now.genTypeConstraints(rtnTypeName, Relation.EQ, env, location));

        for (Map.Entry<ExceptionTypeSym, String> tl : funcSym.exceptions.entrySet()) {
            if (!parent.isCheckedException(tl.getKey(), extern)) {
                throw new SemanticException("Unchecked exception: " + tl.getKey().getName(),
                    location);
            }
        }
        return now;
    }

    @Override
    public ExpOutcome genIFConstraints(VisitEnv env, boolean tail_position)
            throws SemanticException {
        //TODO: Assuming value is a Name for now
        Context beginContext = env.inContext;
        Context endContext = new Context(Utils.getLabelNamePc(toSHErrLocFmt()),
                Utils.getLabelNameLock(toSHErrLocFmt()));
        Map<String, String> dependentLabelMapping = new HashMap<>();

        List<String> argValueLabelNames = new ArrayList<>();

        PathOutcome psi = new PathOutcome(new PsiUnit(endContext));
        ExpOutcome ao = null;

        for (Expression arg : args) {
            ao = arg.genIFConstraints(env, false);
            psi.joinExe(ao.psi);
            argValueLabelNames.add(ao.valueLabelName);

            env.inContext = Utils.genNewContextAndConstraints(env, false,
                    ao.psi.getNormalPath().c, beginContext.lambda, arg.nextPcSHL(), arg.location);
//            env.inContext = new Context(
//                    Utils.joinLabels(ao.psi.getNormalPath().c.pc, beginContext.pc),
//                    beginContext.lambda);
        }

        String funcName;
        String ifNamePc; // currentMethod.PC
        FuncSym funcSym;
        String namespace = "";
        Label ifFuncCallPcBefore, ifFuncCallPcAfter, ifFuncGammaLock;

        ExpOutcome vo = null;

        boolean externalCall = false;
        InterfaceSym externalContractSym = null;
        VarSym externalTargetSym = null;
        String ifContRtn = null;
        if (!(value instanceof Name)) {
            if (value instanceof Attribute att) {
                //  the case: a.b(c) where a is a contract or an array, b is a function and c are the arguments
                // att = a.b

                externalCall = true;
                vo = att.value.genIFConstraints(env, false);
                psi.joinExe(vo.psi);
                ifContRtn = vo.valueLabelName; // a..lbl

                //TODO: assuming a's depth is 1
                funcName = (att.attr).id;
                String varName = ((Name) att.value).id;
                VarSym var = env.getVar(varName);
                if (var.typeSym instanceof ArrayTypeSym arrayTypeSym) {
                    //TODO: change the hard-code style

                    TypeSym arrayTSym = arrayTypeSym.valueType;
                    String arrayTName = arrayTSym.toSHErrLocFmt();
                    if (funcName.equals("pop")) {
                        // requires pc => integrity of the array var
                        Utils.contextFlow(env, psi.getNormalPath().c, endContext, location);
                        ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
                        env.cons.add(
                                new Constraint(new Inequality(ifNamePc, ifContRtn), env.hypothesis(),
                                        location,
                                        "Current control flow must be trusted to call this method"));
                        // pc => l
                        if (!tail_position) {
                            env.cons.add(new Constraint(
                                    new Inequality(psi.getNormalPath().c.lambda, beginContext.lambda),
                                    env.hypothesis(), location,
                                    Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
                        }
                        return new ExpOutcome(ifNamePc, psi);
                    } else if (funcName.equals("push")) {
                        // require one T, return void
                        // requires pc => integrity of the array var
                        // require the element => integrity of the array var
                        Expression arg = args.get(0);
                        ExpOutcome argOutcome = arg.genIFConstraints(env, false);
                        psi.join(argOutcome.psi);
                        String argLabel = argOutcome.valueLabelName;
                        Utils.contextFlow(env, psi.getNormalPath().c, endContext, location);
                        ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
                        env.cons.add(
                                new Constraint(new Inequality(ifNamePc, var.ifl.toSHErrLocFmt()), env.hypothesis(),
                                        location, env.curContractSym().getName(),
                                        "Current control flow must be trusted to call this method"));
                        // pc => ?
                        env.cons.add(
                                new Constraint(new Inequality(argLabel, var.ifl.toSHErrLocFmt()), env.hypothesis(),
                                        location, env.curContractSym().getName(),
                                        "Current control flow must be trusted to call this method"));
                        if (!tail_position) {
                            env.cons.add(new Constraint(
                                    new Inequality(psi.getNormalPath().c.lambda, beginContext.lambda),
                                    env.hypothesis(), location, env.curContractSym().getName(),
                                    Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
                        }
                        return new ExpOutcome(ifNamePc, psi);
                    } else if (funcName.equals("length")) {
                        // return uint the same as
                        Utils.contextFlow(env, psi.getNormalPath().c, endContext, location);
                        ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
                        if (!tail_position) {
                            env.cons.add(new Constraint(
                                    new Inequality(psi.getNormalPath().c.lambda, beginContext.lambda),
                                    env.hypothesis(), location, env.curContractSym().getName(),
                                    Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
                        }
                        return new ExpOutcome(var.ifl.toSHErrLocFmt(), psi);
                    } else {
                        throw new SemanticException("Unrecognized operator", location);
                    }
                }

                externalTargetSym = var;
                namespace = var.toSHErrLocFmt();
                TypeSym conType = var.typeSym;
                externalContractSym = env.getContract(conType.getName());

                env.addSigReq(namespace, conType.getName());
                ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
                InterfaceSym contractSym = env.getContract(conType.getName());
                funcSym = contractSym.getFunc(funcName);
                if (funcSym == null)
                    throw new SemanticException("not found: " + conType.getName() + "." + funcName, location);

                dependentLabelMapping.put(funcSym.sender().toSHErrLocFmt(), env.thisSym().toSHErrLocFmt());
                dependentLabelMapping.put(contractSym.any().toSHErrLocFmt(), env.curContractSym().any().toSHErrLocFmt());

                ifFuncCallPcBefore = funcSym.externalPc();
                ifFuncCallPcAfter = funcSym.internalPc();
                ifFuncGammaLock = funcSym.callGamma();
            } else {
                throw new Error("Internal compiler error" + location.errString());
            }
        } else {
            // a(b) - local contract call
            funcName = ((Name) value).id;
//            if (funcName.equals(Utils.SUPER_KEYWORD)) {
//                funcName = Utils.genSuperName(env.curContractSym().getName());
//            }
            ifNamePc = Utils.getLabelNamePc(scopeContext.getSHErrLocName());
            if (!env.containsFunc(funcName)) {
                if (env.containsContract(funcName) || Utils.isPrimitiveType(funcName)) { //type cast
                    if (args.size() != 1) {
                        throw new SemanticException("cast must have one argument", location);
                    }
                    String ifNameArgValue = argValueLabelNames.get(0);
                    Utils.contextFlow(env, psi.getNormalPath().c, endContext,
                            args.get(0).location);
                    // env.outContext = endContext;
                    if (!tail_position) {
                        env.cons.add(new Constraint(
                                new Inequality(psi.getNormalPath().c.lambda, beginContext.lambda),
                                env.hypothesis(), location, env.curContractSym().getName(),
                                Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
                    }
                    return new ExpOutcome(ifNameArgValue, psi);
                } else {
                    throw new SemanticException("method not found: " + funcName, location);
                }
            }
            funcSym = env.getFunc(funcName);

//            dependentLabelMapping.put(funcSym.sender().toSHErrLocFmt(), env.sender().toSHErrLocFmt());
            dependentLabelMapping.put(funcSym.sender().toSHErrLocFmt(), env.inContext.pc);

            ifFuncCallPcBefore = funcSym.externalPc();
            ifFuncCallPcAfter = funcSym.internalPc();
            ifFuncGammaLock = funcSym.callGamma();
        }

        // build hypothesis for sender and this
        // make sender equal to this
//        Inequality senderHypo = new Inequality(
//                funcSym.sender().toSHErrLocFmt(),
//                CompareOperator.Eq,
//                env.curContractSym.toSHErrLocFmt()
//        );
        // env.hypothesis().add(senderHypo);
        // ++createdHypoCount;

        // if external call and the target address is final, make this equal to the target address
        if (externalCall && callSpec != null) {
            PathOutcome co = callSpec.genIFConstraints(env, false);
            psi.joinExe(co);
            env.inContext = Utils.genNewContextAndConstraints(env, false, co.getNormalPath().c, beginContext.lambda, callSpec.nextPcSHL(), callSpec.location);
        }

        if (externalCall) {
            if (externalTargetSym.isFinal) {
                dependentLabelMapping.put(
                        externalContractSym.thisSym().toSHErrLocFmt(),
                        externalTargetSym.toSHErrLocFmt());
            } else {
                dependentLabelMapping.put(
                        externalContractSym.thisSym().toSHErrLocFmt(),
                        ifContRtn); // XXX is this correct? Seems like this is the label of the contract value.
            }
        }

//        System.err.println("Call: " + funcName);
        for (int i = 0; i < args.size(); ++i) {
            Expression arg = args.get(i);
            VarSym argSym = funcSym.parameters.get(i);
            if (argSym.isPrincipalVar()) {

                if (arg instanceof Name) {
                    VarSym valueSym = (VarSym) env.getVar(((Name) arg).id);
                    if (valueSym.isPrincipalVar()) {
//                        System.err.println("dependent " + argSym.typeSym + " -> " + valueSym.toSHErrLocFmt());
                        dependentLabelMapping.put(argSym.toSHErrLocFmt(), valueSym.toSHErrLocFmt());
                    }
                }
            }

            // env.prevContext = prevContext = tmp;
            String ifNameArgValue = argValueLabelNames.get(i);
            Label ifArgLabel = funcSym.getLabelArg(i);
            assert ifArgLabel != null : argSym.getName();
            env.cons.add(
                    new Constraint(
                            new Inequality(
                                    ifNameArgValue,
                                    Relation.LEQ,
                                    ifArgLabel.toSHErrLocFmt(dependentLabelMapping)
                            ),
                            env.hypothesis(), arg.location, env.curContractSym().getName(),
                            "Input to the " + Utils.ordNumString(i + 1)
                                    + " argument must be trusted enough")
            );
            env.cons.add(
                    new Constraint(
                            new Inequality(
                                    ifNamePc,
                                    Relation.LEQ,
                                    ifArgLabel.toSHErrLocFmt(dependentLabelMapping)),
                            env.hypothesis(), arg.location, env.curContractSym().getName(),
                            "Current control flow must be trusted to feed the " + Utils.ordNumString(i + 1)
                            + "-th argument value")
            );
        }

        if (externalCall) {
//            String tem = ((Attribute) value).value.toSHErrLocFmt();
            env.cons.add(
                    new Constraint(
                            new Inequality(ifContRtn, ifFuncCallPcBefore.toSHErrLocFmt(dependentLabelMapping)),
                    env.hypothesis(), location, env.curContractSym().getName(),
                    "Target contract must be trusted to call this method"));
        }


        PathOutcome expPsi = new PathOutcome(new PsiUnit(new Context(
                Utils.joinLabels(psi.getNormalPath().c.pc, funcSym.endPc().toSHErrLocFmt(dependentLabelMapping)),
//                funcSym.getLabelNameCallGamma()
                ifFuncGammaLock.toSHErrLocFmt(dependentLabelMapping)
        )));

        for (Entry<ExceptionTypeSym, String> exp : funcSym.exceptions.entrySet()) {
            ExceptionTypeSym curSym = exp.getKey();
            String expLabelName = exp.getValue();
            expPsi.set(curSym, new PsiUnit(
                    new Context(
                            Utils.joinLabels(expLabelName, funcSym.externalPcSLC()),
                            Utils.joinLabels(ifFuncGammaLock.toSHErrLocFmt(dependentLabelMapping),
                                    ifFuncCallPcAfter.toSHErrLocFmt(dependentLabelMapping))),
                    true)); //TODO: dependent
            //PsiUnit psiUnit = env.psi.get(curSym);
            //env.cons.add(new Constraint(new Inequality(Utils.makeJoin(expLabelName, ifNameFuncCallPcAfter), psiUnit.pc), env.hypothesis, location, env.curContractSym.name,
            //"Exception " + curSym.name + " is not trusted enough to throw"));
        }

        //TODO

        env.cons.add(
                new Constraint(new Inequality(ifNamePc, ifFuncCallPcBefore.toSHErrLocFmt(dependentLabelMapping)), env.hypothesis(),
                        location, env.curContractSym().getName(),
                        "Current control flow must be trusted to call this method"));
        env.cons.add(new Constraint(new Inequality(ifFuncCallPcBefore.toSHErrLocFmt(dependentLabelMapping),
                Utils.joinLabels(ifFuncCallPcAfter.toSHErrLocFmt(dependentLabelMapping), beginContext.lambda)), env.hypothesis(),
                location, env.curContractSym().getName(),
                "Calling this function does not respect static reentrancy locks"));


        if (externalCall) {
            env.cons.add(new Constraint(
                    new Inequality(Utils.joinLabels(ifContRtn,
                            ifFuncGammaLock.toSHErrLocFmt(dependentLabelMapping)),
                            Relation.EQ, endContext.lambda), env.hypothesis(), location,
                    env.curContractSym().getName(),
                    "Calling this function does not respect static reentrancy locks"));
        }
        if (!tail_position) {
//            env.cons.add(new Constraint(
//                    new Inequality(Utils.joinLabels(ifFuncCallPcAfter.toSHErrLocFmt(dependentLabelMapping), ifFuncGammaLock.toSHErrLocFmt(dependentLabelMapping)),
//                            Relation.EQ, endContext.lambda), env.hypothesis(), location,
//                    env.curContractSym().getName(),
//                    typecheck.Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));

//            env.cons.add(new Constraint(
//                    new Inequality(psi.getNormalPath().c.lambda, beginContext.lambda),
//                    env.hypothesis(), location, env.curContractSym().getName(),
//                    typecheck.Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
            // apply the seq rule
        }

        String ifNameFuncRtnValue = funcSym.rtn.toSHErrLocFmt(dependentLabelMapping);
//        System.err.println("return label: " + ifNameFuncRtnValue);
        // String ifNameFuncRtnLock = funcSym.getLabelNameRtnLock();
        psi.joinExe(expPsi);
        Utils.contextFlow(env, psi.getNormalPath().c, endContext, location);
        psi.setNormalPath(endContext);
        Constraint ln_to_t = new Constraint(new Inequality(psi.getNormalPath().c.pc, ifNameFuncRtnValue),
                env.hypothesis(), location, env.curContractSym().getName(), "ln_to_t");
        env.cons.add(ln_to_t);
        return new ExpOutcome(ifNameFuncRtnValue, psi);
    }

    @Override
    public compile.ast.Expression solidityCodeGen(List<Statement> result, CompileEnv code) {
        List<compile.ast.Expression> argumentExps = new ArrayList<>();
        for (Expression arg: args) {
            argumentExps.add(arg.solidityCodeGen(result, code));
        }
        compile.ast.Call callExp;
        String funcName;
        // hash the name if not private method
        if (builtIn || isCast) {
            funcName = value instanceof Name ? ((Name) value).id : ((Attribute) value).attr.id;
        } else {
            assert funcSym != null;
            if (!(value instanceof Name))  {// || funcSym.isPublic()) {
                // external call
                funcName = Utils.methodNameHash(funcSym.funcName, funcSym.plainSignature());
            } else {
                funcName = funcSym.funcName;
            }
        }

        if (value instanceof Name) {
            // internal call
            callExp = new compile.ast.Call(funcName, argumentExps);
        } else {
            // external call
            assert value instanceof Attribute;
            compile.ast.Expression target = ((Attribute) value).value.solidityCodeGen(result, code);
            // String funcName = ((Attribute) value).attr.id;
            if (builtIn && funcName.equals("length")) {
                return new Attr(target, funcName);
            }
            callExp = new ExternalCall(target, funcName, argumentExps, callSpec != null ? callSpec.solidityCodeGen(result, code) : null);
        }
        assert ntced : "funcSym being null: " + callExp.toSolCode() + " " + builtIn;
        assert !(funcName.equals("send") && !builtIn);
        if (builtIn) {
            return compile.Utils.translateBuiltInFunc(callExp);
        } else if (isCast || funcSym.exceptions.size() == 0) {
            return callExp;
        } else {
            // statVar, dataVar = call(...);
            // if (statVar != 0) return statVar, dataVar
            // else tempVar = parse(dataVar);
            // replace call with tempVar
            SingleVar statVar = new SingleVar(code.newTempVarName());
            SingleVar dataVar = new SingleVar(code.newTempVarName());
            result.add(new VarDec(compile.Utils.PRIMITIVE_TYPE_UINT, statVar.name()));
            result.add(new VarDec(compile.Utils.PRIMITIVE_TYPE_BYTES, dataVar.name()));
            SingleVar tempVar = null;
            if (!funcSym.returnType.isVoid()) {
                tempVar = new SingleVar(code.newTempVarName());
                result.add(new VarDec(new PrimitiveType(funcSym.returnType.getName()), tempVar.name()));
            }
            result.add(new Assign(
                    List.of(statVar, dataVar),
                    callExp
            ));

            // map exceptionID
            IfStatement mapExpIds = null;
            int i = 1;
            for (Entry<ExceptionTypeSym, String> entry: funcSym.exceptions.entrySet()) {
                IfStatement ifexp = new IfStatement(
                        new BinaryExpression(compile.Utils.SOL_BOOL_EQUAL, statVar, new Literal(String.valueOf(i))),
                        List.of(new Assign(statVar, new Literal(String.valueOf(code.getExceptionId(entry.getKey()))))),
                        mapExpIds == null ? null : List.of(mapExpIds)
                        );
                mapExpIds = ifexp;
                ++i;
            }
            if (mapExpIds != null) {
                result.add(mapExpIds);
            }

            compile.ast.Expression condition = new BinaryExpression(compile.Utils.SOL_BOOL_NONEQUAL,
                    statVar, new Literal(compile.Utils.RETURNCODE_NORMAL));
            IfStatement test = new IfStatement(condition,
                    List.of(new Return(List.of(statVar, dataVar))));
            result.add(test);

            if (funcSym.returnType.isVoid()) {
                return new Pass();
            } else {
                result.add(new Assign(tempVar,
                        code.decodeVars(
                                funcSym.parameters.stream()
                                        .map(para -> new PrimitiveType(para.typeSym.getName()))
                                        .collect(
                                                Collectors.toList()),
                                dataVar)));
                return tempVar;
            }
        }
    }

//    public String toSolCode() {
//        // logger.debug("toSOl: Call");
//        String funcName = value.toSolCode();
////        if (Utils.isBuiltinFunc(funcName)) {
////            return Utils.transBuiltinFunc(funcName, this);
////        }
//        String argsCode = "";
//        boolean first = true;
//        for (Expression exp : args) {
//            if (!first) {
//                argsCode += ", ";
//            } else {
//                first = false;
//            }
//            argsCode += exp.toSolCode();
//        }
//
//        return CompileEnv.toFunctionCall(funcName, argsCode);
//    }

    @Override
    public List<Node> children() {
        List<Node> rtn = new ArrayList<>();
        rtn.add(value);
        if (callSpec != null) rtn.add(callSpec);
        rtn.addAll(args);
        return rtn;
    }

    @Override
    public boolean typeMatch(Expression expression) {
        if (!(expression instanceof Call &&
                super.typeMatch(expression))) {
            return false;
        }

        Call c = (Call) expression;

        boolean bothArgsNull = c.args == null && args == null;

        if (!bothArgsNull) {
            if (args == null || c.args == null || args.size() != c.args.size()) {
                return false;
            }
            int index = 0;
            while (index < args.size()) {
                if (!args.get(index).typeMatch(c.args.get(index))) {
                    return false;
                }
                ++index;
            }
        }

        return true;
    }

    /**
     * Check if a type cast
     */
    public boolean isCast(VisitEnv env) {
        if (value instanceof Name) {
            // a(b)
            String funcName = ((Name) value).id;
            Sym s = env.getCurSym(funcName);
            assert s != null;
            if (!(s instanceof FuncSym)) {
                if (s instanceof InterfaceSym || s instanceof BuiltinTypeSym) {
                    return true;
                }
                assert false;
                return false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isExternal() {
        return value instanceof Attribute;
    }

    @Override
    public java.util.Map<String, compile.ast.Type> readMap(CompileEnv code) {
        Map<String, Type> result = new HashMap<>();
        for (Expression arg: args) {
            result.putAll(arg.readMap(code));
        }
        return result;
    }

    public void checkAndRenameSuper(String extendsContractName) {
        if (value instanceof Name name) {
            if (name.id.equals(Utils.SUPER_KEYWORD)) {
                name.id = Utils.genSuperName(extendsContractName);
            }
        }
    }
}
