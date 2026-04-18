// the main class of STC

import ast.SourceFile;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ast.Type;
import java_cup.runtime.ParserException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import typecheck.exceptions.SemanticException;
import typecheck.exceptions.TypeCheckFailure;

import parser.Parser;
import parser.LexerTest;

/**
 * The main program of SCIF compiler. It accepts arguments and options to perform parsing,
 * typechecking, and compiling SCIF code.
 */
@Command(name = "SCIF", version = "SCIF 0.5.0", mixinStandardHelpOptions = true,
        description = "A set of tools for a new smart contract language with information flow control, SCIF.")
public class SCIF implements Callable<Integer> {

    @Parameters(arity = "1..*", description = "The source code file(s).")
    private File[] m_inputFiles;

    @Option(names = "-debug")
    private boolean m_debug;

    @Option(names = "-lg", arity = "1..*", description = "The log directory.")
    private String[] m_logDir;

    @Option(names = "-o", arity = "1..*", description = "The output file.")
    private String[] m_solFileNames;

    @ArgGroup(exclusive = true)
    private FuncRequest m_funcRequest;

    private static class FuncRequest {

        @Option(names = {"-t",
                "--typechecker"}, required = true, description = "Information flow typecheck: constraints as log")
        boolean typecheck;
        @Option(names = {"-p", "--parser"}, required = true, description = "Parse: ast json as log")
        boolean parse;
        @Option(names = {"-l", "--lexer"}, required = true, description = "Tokenize")
        boolean tokenize;
        @Option(names = {"-c",
                "--compiler"}, required = true, description = "Compile to Solidity (default)")
        boolean compile;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SCIF()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Typecheck input code files, and return the manipulated AST.
     *
     * @param logDirs
     * @return The manipulated AST.
     */
    private List<SourceFile> _typecheck(String[] logDirs)
            throws TypeCheckFailure, IOException {
        try {
            File logDir = new File("./.scif");
            if (logDirs != null) {
                logDir = new File(logDirs[0]);
            }
            logDir.mkdirs();

            List<File> files = new ArrayList<>();
            for (File file : m_inputFiles) {
                files.add(file);
            }

            List<SourceFile> roots;
//            try {
            roots = Preprocessor.preprocess(files);
//            } catch (Parser.SyntaxError e) {
//                return null;
//            }
            if (roots == null) {
                return null;
            }

//            try {
            if (!TypeChecker.regularTypecheck(roots, logDir, m_debug)) {
                return null;
            }
//            } catch (Parser.SyntaxError e) {
//                return null;
//            }
//
//            boolean passNTC = true;
//            if (!Utils.emptyFile(outputFileName))
//                passNTC = runSLC(outputFileName);
//            if (roots == null) {
//                passNTC = false;
//            }
//            if (!passNTC) {
//                return null;
//            }
            // System.out.println("["+ outputFileName + "]");


//            List<File> IFCConsFiles = new ArrayList<>();
//        for (int i = 0; i < roots.size(); ++i) {
//            File IFCConsFile;
//            if (outputFileNames == null || outputFileNames.length <= i + 1) {
//                IFCConsFile = new File(logDir, "ifc" + i + ".cons");
//            } else {
//                IFCConsFile = new File(outputFileNames[i + 1]);
//            }
//            IFCConsFiles.add(IFCConsFile);
//        }

//        System.out.println("\nInformation Flow Type Checking:");
            boolean passIFC = false;

            passIFC = TypeChecker.ifcTypecheck(roots, logDir, m_debug);

            // System.out.println("["+ outputFileName + "]" + "Information Flow Typechecking finished");
            // // logger.debug("running SHErrLoc...");
            // boolean passIFC = runSLC(outputFileName);

//            return (passNTC && passIFC) ? roots : null;
            return (passIFC) ? roots : null;
        } catch (Parser.SyntaxError e) {
            return null;
        } catch (SemanticException e) {
            System.err.println(e.getMessage());
            return List.of();
        }
    }

    /**
     * SCIF CLI starts and runs one of the following tasks specified by the options <code>m_funcRequest</code>.
     * 1. compile (default): compile the given SCIF source files to Solidity
     * 2. typecheck
     * 3. parse
     * 4. tokenize
     */
    @Override
    public Integer call() {

        logger.trace("SCIF starts");
        if (m_funcRequest == null || m_funcRequest.compile) {
            List<SourceFile> roots;
            try {
                roots = _typecheck(null);
            } catch (TypeCheckFailure e) {
                System.out.println(e.getMessage());
                return 0;
            } catch (Exception e) {
                System.out.println("Unexpected exception:");
                e.printStackTrace();
                return 0;
            }
            // logger.debug("finished typecheck, compiling...");
            if (roots == null) {
                // TODO(steph): use a more informative and helpful way
                return 1;
            }
            File solFile;
            if (m_solFileNames == null) {
                Path input0 = m_inputFiles[0].toPath();
                String basename = input0.getFileName().toString();
                int dotIndex = basename.lastIndexOf('.');
                solFile = new File((dotIndex == -1)
                        ? basename + ".sol"
                        : basename.substring(0, dotIndex) + ".sol");
                // solFile = File.createTempFile("scif_", ".sol");
                if (m_debug) System.err.println("Output to file: " + solFile);
                // solFile.deleteOnExit();
            } else {
                solFile = new File(m_solFileNames[0]);
            }
            Map<String, List<SourceFile>> rootMap = new HashMap<>();
            for (SourceFile r: roots) {
                // rootMap.put(r.getSourceFilePath(), r);
                rootMap.computeIfAbsent(r.getSourceFilePath(), k -> new ArrayList<>()).add(r);
            }
            for (File f: m_inputFiles) {
                // SourceFile r = rootMap.get(f.getAbsolutePath());
                List<SourceFile> rs = rootMap.get(f.getAbsolutePath());
                if (rs == null) return 0;
                SolCompiler.compile(rs, solFile);
            }
        } else if (m_funcRequest.typecheck) {
            try {
                _typecheck(m_logDir);
            } catch (TypeCheckFailure e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return 0;
            }
        } else if (m_funcRequest.parse) {
            File astOutputFile = m_logDir == null ? null : new File(m_logDir[0] + newFileName("parse", "result"));
            try {
                Parser.parse(m_inputFiles[0], astOutputFile);
            } catch (Parser.SyntaxError e) {
                /* skip */
            }
        } else if (m_funcRequest.tokenize) {
            LexerTest.tokenize(m_inputFiles[0]);
        } else {
            logger.error("No funcRequest specified, this should never happen!");
        }

        logger.trace("SCIF finishes");
        return 0;
    }

    public static String newFileName(String prefix, String suffix) {
        int counter = 0;
        String filename;
        while (true) {
            filename = prefix +String.valueOf(counter) + "." + suffix;
            if (!fileNames.contains(filename)) {
                fileNames.add(filename);
                return filename;
            }
            counter += 1;
        }
    }

    protected static final Logger logger = LogManager.getLogger(SCIF.class);

    static Set<String> fileNames = new HashSet<>();
}
