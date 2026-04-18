package ast;

import compile.CompileEnv;
import compile.ast.Statement;
import compile.ast.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Inequality;
import typecheck.sherrlocUtils.Relation;
import typecheck.*;

import java.util.ArrayList;

/**
 * Represent an expression value[index].
 * value is of type map or array, and index's type should be right accordingly.
 */
public class Subscript extends TrailerExpr {

    Expression index;

    public Subscript(Expression v, Expression i) {
        value = v;
        index = i;
    }

    @Override
    public VarSym getVarInfo(NTCEnv env) {
        VarSym valueVarSym = value.getVarInfo(env);
        VarSym subscriptVarSym = null;
        if (valueVarSym.typeSym instanceof MapTypeSym) {

            subscriptVarSym = new VarSym(
                    valueVarSym.getName() + ".sub",
                    ((MapTypeSym) valueVarSym.typeSym).valueType,
                    null,
                    location,
                    valueVarSym.defContext(),
                    false,
                    false,
                    true
            );
        } else if (valueVarSym.typeSym instanceof ArrayTypeSym) {
            subscriptVarSym = new VarSym(
                valueVarSym.getName() + ".sub",
                    ((ArrayTypeSym) valueVarSym.typeSym).valueType,
                    null,
                    location,
                    valueVarSym.defContext(),
                    false,
                    false,
                    true
            );
        }
        return subscriptVarSym;
    }

    @Override
    public ScopeContext genTypeConstraints(NTCEnv env, ScopeContext parent) throws SemanticException {
        ScopeContext now = new ScopeContext(this, parent);
        VarSym valueVarSym = value.getVarInfo(env);
        ScopeContext idx = index.genTypeConstraints(env, now);
        value.genTypeConstraints(env, now);

        if (valueVarSym.typeSym instanceof DepMapTypeSym) {
            // index must match, and must be a final address/contract or a principal
            boolean validIndex = true;
            if (index instanceof Name) {
                VarSym indexSym = index.getVarInfo(env);
                if (!indexSym.isPrincipalVar()) {
                    validIndex = false;
                }
            } else {
                validIndex = false;
            }
            if (!validIndex) {
                // TODO(steph): fix exceptions
                throw new RuntimeException("Must use a final address/contract to access a dependent map: " + ((Name) index).id + " at " + location.errString());
            }
            return now;
        } else if (valueVarSym.typeSym instanceof MapTypeSym) {
            MapTypeSym typeInfo = (MapTypeSym) valueVarSym.typeSym;
            // index matches the keytype
            env.addCons(idx.genTypeConstraints(typeInfo.keyType.getName(), Relation.LEQ, env, location));
            // valueType matches the result exp
            env.addCons(now.genTypeConstraints(typeInfo.valueType.getName(), Relation.EQ, env, location));
            return now;
        } else if (valueVarSym.typeSym instanceof ArrayTypeSym) {
            ArrayTypeSym typeInfo = (ArrayTypeSym) valueVarSym.typeSym;
            env.addCons(idx.genTypeConstraints(Utils.BuiltinType2ID(BuiltInT.UINT), Relation.LEQ, env, location));
            env.addCons(now.genTypeConstraints(typeInfo.valueType.getName(), Relation.EQ, env, location));
            return now;
        } else {
            throw new RuntimeException("Subscript: value type not found: " + value);
        }
    }

    @Override
    public ExpOutcome genIFConstraints(VisitEnv env, boolean tail_position) throws SemanticException {
        Context beginContext = env.inContext;
        Context endContext = new Context(typecheck.Utils.getLabelNamePc(toSHErrLocFmt()),
                typecheck.Utils.getLabelNameLock(toSHErrLocFmt()));

        Map<String, String> dependentMapping = new HashMap<>();
        VarSym valueVarSym = value.getVarInfo(env, false, dependentMapping);

        String ifNameValue = valueVarSym.labelNameSLC();
        // String ifNameRtnValue = ifNameValue + "." + "Subscript" + location.toString();
        String ifNameRtnValue = toSHErrLocFmt();
        ExpOutcome io = index.genIFConstraints(env, tail_position);


        // String ifNameRtnLock = "";
        if (valueVarSym.typeSym instanceof DepMapTypeSym depMapTypeSym) {
            // value[index] where value is of dependent map type
            // precondition: the type of index and value match; index is a final address/contract or a principal
            // the result value of this expression has the label dependent to index

            // DepMapTypeSym typeSym = (DepMapTypeSym) valueVarSym.typeSym;
            Map<String, String> indexMapping = new HashMap<>();
            VarSym indexVarSym = index.getVarInfo(env, tail_position, indexMapping);
            // logger.debug("subscript/DepMap:");
            // logger.debug("lookup at: " + index.toString());
            // logger.debug(indexVarSym.toString());
            String ifNameIndex = indexVarSym.toSHErrLocFmt();

            if (indexVarSym.isPrincipalVar()) {
//                // logger.debug("typename {} to {}", valueVarSym.typeSym.getName(), ifNameIndex);
                System.err.println("typename " + depMapTypeSym.label() + " to " + ifNameIndex);

                dependentMapping.put(depMapTypeSym.key().toSHErrLocFmt(), ifNameIndex);
                String ifDepMapValue = depMapTypeSym.label().toSHErrLocFmt(dependentMapping);
//                String ifDepMapValue = depMapTypeSym.label().toSHErrLocFmt(depMapTypeSym.key().toSHErrLocFmt(),
//                        ifNameIndex);
//
//                env.addTrustConstraint(
//                        new Constraint(new Inequality(ifDepMapValue, Relation.EQ, ifNameRtnValue),
//                                env.hypothesis(), location, env.curContractSym().getName(),
//                                "Integrity level of the subscripted value is not expected"));
                return new ExpOutcome(ifDepMapValue, io.psi);

            } else {
                assert false;
                return null;
            }
        } else {
            Context indexContext = io.psi.getNormalPath().c;
            String ifNameIndex = io.valueLabelName;
            //ifNameRtnValue =
            //        scopeContext.getSHErrLocName() + "." + "Subscript" + location.toString();
            env.cons.add(new Constraint(new Inequality(ifNameValue, Relation.EQ, ifNameRtnValue),
                    env.hypothesis(), location, env.curContractSym().getName(),
                    "Integrity level of the subscript value is not trustworthy enough"));

            // env.cons.add(new Constraint(new Inequality(ifNameIndex, ifNameRtnValue), env.hypothesis, location));
            // ifNameRtnLock = indexContext.lambda;
            return new ExpOutcome(ifNameRtnValue, io.psi);
        }
    }

    @Override
    public compile.ast.Expression solidityCodeGen(List<Statement> result, CompileEnv code) {
        return new compile.ast.Subscript(value.solidityCodeGen(result, code), index.solidityCodeGen(result, code));
    }

    @Override
    public VarSym getVarInfo(VisitEnv env, boolean tail_position, Map<String, String> dependentMapping)
            throws SemanticException {
        VarSym rtnVarSym = null;
        VarSym valueVarSym = value.getVarInfo(env, false, dependentMapping);
        String ifNameValue = valueVarSym.labelNameSLC();
//        String ifNameRtn = ifNameValue + "." + "Subscript" + location.toString();
        String ifNameRtn = scopeContext.getSHErrLocName() + "." + "Subscript" + location.toString();
        if (valueVarSym.typeSym instanceof DepMapTypeSym depMapTypeSym) {
            // check if the index value is a principal
            Map<String, String> indexMapping = new HashMap<>();
            VarSym indexVarSym = index.getVarInfo(env, tail_position, indexMapping);
            String ifNameIndex = indexVarSym.toSHErrLocFmt();
            if (indexVarSym.isPrincipalVar()) {

                TypeSym rtnTypeSym = depMapTypeSym.valueType;
                dependentMapping.put(depMapTypeSym.key().toSHErrLocFmt(), ifNameIndex);
                rtnVarSym = new VarSym(ifNameRtn, rtnTypeSym, valueVarSym.ifl, location,
                        rtnTypeSym.defContext(), false, false, false);
                env.cons.add(
                        new Constraint(new Inequality(depMapTypeSym.label().toSHErrLocFmt(dependentMapping), rtnVarSym.labelNameSLC()), env.hypothesis(), location,
                                env.curContractSym().getName(),
                                "Label of the subscript value"));

//                String ifDepMapValue = (valueVarSym).ifl.toSHErrLocFmt(valueVarSym.typeSym.name(),
//                        ifNameIndex);

//                env.cons.add(
//                        new Constraint(new Inequality(ifDepMapValue, ifNameRtn), env.hypothesis,
//                                location, env.curContractSym.name(),
//                                "Label of the subscript variable"));

            } else {
//                logger.error(,
//                        locToString());
                assert false: "non-address type variable as index to access a dependent map: at " + location.errString();
                //System.out.println("ERROR: non-address type variable as index to access DEPMAP @" + locToString());
                return null;
            }
        } else {
            String ifNameIndex = index.genIFConstraints(env, tail_position).valueLabelName;

            TypeSym rtnTypeSym = new BuiltinTypeSym(ifNameRtn);
            rtnVarSym = new VarSym(ifNameRtn, rtnTypeSym, valueVarSym.ifl, location,
                    valueVarSym.defContext(), false, false, false);
            env.cons.add(
                    new Constraint(new Inequality(ifNameValue, rtnVarSym.labelNameSLC()), env.hypothesis(), location,
                            env.curContractSym().getName(),
                            "Label of the subscript value"));

            env.cons.add(
                    new Constraint(new Inequality(ifNameIndex, rtnVarSym.labelNameSLC()), env.hypothesis(), location,
                            env.curContractSym().getName(),
                            "Label of the subscript index value"));

        }
        return rtnVarSym;
    }

//    @Override
//    public String toSolCode() {
//        String i = index.toSolCode();
//        String v = value.toSolCode();
//        return v + "[" + i + "]";
//    }

    @Override
    public ArrayList<Node> children() {
        ArrayList<Node> rtn = new ArrayList<>();
        rtn.add(value);
        rtn.add(index);
        return rtn;
    }

    @Override
    public boolean typeMatch(Expression expression) {
        return expression instanceof Subscript &&
                super.typeMatch(expression) &&
                index.typeMatch(((Subscript) expression).index);
    }

    @Override
    public java.util.Map<String, compile.ast.Type> readMap(CompileEnv code) {
        Map<String, Type> result = index.readMap(code);
        result.putAll(value.readMap(code));
        return result;
    }

    @Override
    public Map<String,? extends Type> writeMap(CompileEnv code) {
        return value.writeMap(code);
    }
}
