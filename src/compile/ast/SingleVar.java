package compile.ast;

import java.util.List;
import typecheck.Utils;

public class SingleVar extends Expression {
    String name;
    public SingleVar(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toSolCode() {
        return translateBuiltInVars(name());
    }

    private String translateBuiltInVars(String name) {
        if (name.equals(Utils.UINTMAX)) {
            return "type(uint256).max";
        } else if (name.equals(Utils.NOW)) {
            return "block.timestamp";
        }
        return name;
    }
}
