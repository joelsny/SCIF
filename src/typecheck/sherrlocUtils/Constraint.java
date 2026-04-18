package typecheck.sherrlocUtils;

import typecheck.CodeLocation;
import typecheck.Utils;

public class Constraint {
    // names are unique strings
    public final Inequality inequality;
    public final Hypothesis hypothesis;
    public final Position position;
    final String contractName;
    public final String explanation;
    public final int weight;

    static final int WEIGHT1 = 5;
    static final int WEIGHT2 = 1;

    /*public Constraint(Inequality inequality, Hypothesis hypothesis, CodeLocation location, String contractName) {
        this.inequality = inequality;
        this.hypothesis = new Hypothesis(hypothesis);
        this.position = location == null ? null : new Position(location);
        this.contractName = contractName;
    }*/

    public Constraint(Inequality inequality, Hypothesis hypothesis, CodeLocation location, String contractName, String explanation) {
        assert location != null;
        this.inequality = inequality;
        this.hypothesis = new Hypothesis(hypothesis);
        this.position = new Position(location);
        this.contractName = position.fileName;
        this.explanation = explanation + "@" + this.contractName;
        if (explanation.equals(Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION)) {
            this.weight = WEIGHT1;
        } else {
            this.weight = WEIGHT2;
        }
    }
    public Constraint(Inequality inequality, Hypothesis hypothesis, CodeLocation location, String explanation) {
        assert location != null;
        this.inequality = inequality;
        this.hypothesis = new Hypothesis(hypothesis);
        this.position = new Position(location);
        this.contractName = position.fileName;
        this.explanation = explanation + "@" + contractName;
        if (explanation.equals(Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION)) {
            this.weight = WEIGHT1;
        } else {
            this.weight = WEIGHT2;
        }
    }
    public Constraint(Inequality inequality, Hypothesis hypothesis, CodeLocation location, String explanation, int weight) {
        this.inequality = inequality;
        this.hypothesis = new Hypothesis(hypothesis);
        this.position = location == null ? null : new Position(location);
        this.contractName = position.fileName;
        this.explanation = explanation + "@" + contractName;
        this.weight = weight;
    }

    /*public Constraint(Inequality inequality, CodeLocation location, String contractName) {
        this.inequality = inequality;
        this.hypothesis = new Hypothesis();
        this.position = location == null ? null : new Position(location);
        this.contractName = contractName;
    }*/

    public Constraint(Inequality inequality, CodeLocation location, String explanation) {
        this.inequality = inequality;
        this.hypothesis = new Hypothesis();
        this.position = location == null ? null : new Position(location);
        this.contractName = position.fileName;
        this.explanation = explanation + "@" + contractName;
        if (explanation.equals(Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION)) {
            this.weight = WEIGHT1;
        } else {
            this.weight = WEIGHT2;
        }
    }
    public Constraint(Inequality inequality, CodeLocation location, String contractName, String explanation) {
        this.inequality = inequality;
        this.hypothesis = new Hypothesis();
        this.position = location == null ? null : new Position(location);
        this.contractName = position.fileName;
        this.explanation = explanation + "@" + this.contractName;
        if (explanation.equals(Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION)) {
            this.weight = WEIGHT1;
        } else {
            this.weight = WEIGHT2;
        }
    }

    public Constraint() {
        inequality = null;
        hypothesis = null;
        position = null;
        contractName = null;
        explanation = "";
        weight = WEIGHT2;
    }

    public String toSherrlocFmt(boolean withHypoAndPosition) {
        if (inequality == null) {
            return "";
        }
        if (withHypoAndPosition && position != null) {
            return inequality.toSherrlocFmt() + " " + hypothesis.toSherrlocFmt()  + ";" + position.toSherrlocFmt(explanation, weight);
        } else {
            return inequality.toSherrlocFmt() + ";";
        }
    }
}
