package typecheck.sherrlocUtils;

import ast.CompareOperator;
import sherrloc.diagnostic.SherrlocDiagnoser;

import java.util.List;

// a <= b means a flows to b
// that is, a >= b in terms of integrity level
// in case of regular typechecking, a <= b means a is supertype of b
public class Inequality {
    String lhs, rhs;
    Relation relation;
    public Inequality(String lhs, Relation relation, String rhs) {
        assert !lhs.startsWith("null");
        assert !rhs.startsWith("null");
        assert !lhs.equals("");
        assert !rhs.equals("");
        this.lhs = lhs;
        this.rhs = rhs;
        this.relation = relation;
    }
    public Inequality(String lhs, CompareOperator co, String rhs) {
        assert !lhs.startsWith("null") : lhs;
        assert !rhs.startsWith("null") : rhs;
        assert !lhs.equals("");
        assert !rhs.equals("");
        this.lhs = lhs;
        this.rhs = rhs;
        if (co == CompareOperator.Eq)
            this.relation = Relation.EQ;
        else if (co == CompareOperator.GtE)
            this.relation = Relation.GEQ;
        else if (co == CompareOperator.LtE)
            this.relation = Relation.LEQ;
    }

    public Inequality(String lhs, String rhs) {
        assert !lhs.startsWith("null");
        assert !rhs.startsWith("null");
        this.lhs = lhs;
        this.rhs = rhs;
        this.relation = Relation.LEQ;
    }

    public String toSherrlocFmt() {
        if (relation == Relation.EQ) {
            return lhs + " == " + rhs;
        } else if (relation == Relation.LEQ) {
            return lhs + " <= " + rhs;
        } else if (relation == Relation.GEQ) {
            return lhs + " >= " + rhs;
        } else {
            //TODO: error   
            return "";
        }
    }

    public sherrloc.constraint.ast.Inequality toSherrlocInequality(SherrlocDiagnoser diagnoser) {
        var e = diagnoser.createElement(lhs, sherrloc.constraint.ast.Position.EmptyPosition());
        var o = diagnoser.createElement(rhs, sherrloc.constraint.ast.Position.EmptyPosition());

        if (relation == Relation.EQ) {
            return diagnoser.createEqualityConstraint(e, o);
        } else if (relation == Relation.LEQ) {
            return diagnoser.createLessThanConstraint(e, o);
        } else {
            return diagnoser.createGreaterThanConstraint(e, o);
        }
    }
}
