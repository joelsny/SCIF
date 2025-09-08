import ast.SourceFile;
import compile.CompileEnv;
import compile.Utils;
import compile.ast.SolNode;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import typecheck.ExceptionTypeSym;
import java.io.FileWriter;
import java.io.IOException;

public class SolCompiler {

    public static void compile(List<SourceFile> roots, File outputFile) {
        // assuming the code typechecks, might need to deal with namespace when multi-contract

        // steph: invariant - all SourcesFile under roots are from same file path.
        logger.trace("compiling starts");

        try {
            new FileWriter(outputFile, false).close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        System.out.println("\nCompiled Solidity code:");
        for (int i = 0; i < roots.size(); i++) {
            SourceFile root = roots.get(i);
            if (i == 0) {
                // mark the first contract/interface in the file so we write imports
                root.markSourceFirstInFile(true);
            }

            CompileEnv env = new CompileEnv();
            // System.err.println("Compiling " + root.getContractName() + ":");
            compile.ast.SourceFile node = root.solidityCodeGen(env);

            node.addStats(env);
            Utils.writeToFile(node, outputFile);
        }
        // Utils.printCode(node);

        logger.trace("compiling finished");
    }

    protected static final Logger logger = LogManager.getLogger();
}
 