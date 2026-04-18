package compile.ast;

import compile.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Emit implements Statement{
    String eventName;
    List<Expression> args;

    public Emit(String eventName, List<Expression> args) {
        this.eventName = eventName;
        this.args = args;
    }

    @Override
    public List<String> toSolCode(int indentLevel) {
        List<String> result = new ArrayList<>();
        Utils.addLine(result, "emit " + eventName + "(" +
                String.join(", ", args.stream().map
                        (value -> value.toSolCode()).collect(Collectors.toList()))
                        + ");",
                indentLevel);
        return result;
    }
}
