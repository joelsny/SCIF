package typecheck;

import ast.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sherrloc.constraint.ast.Constructor;
import sherrloc.constraint.ast.Hypothesis;
import sherrloc.diagnostic.DiagnosticOptions;
import sherrloc.diagnostic.ErrorDiagnosis;
import sherrloc.diagnostic.explanation.Entity;
import sherrloc.diagnostic.explanation.Explanation;
import sherrloc.graph.Variance;
import typecheck.exceptions.SemanticException;
import typecheck.sherrlocUtils.Constraint;
import typecheck.sherrlocUtils.Inequality;

import java.io.*;
import java.util.*;


public class Utils {

    public static final String[] BUILTIN_TYPE_NAMES =
            new String[]{"bool", "address", "bytes", "string", "void", "uint", "principal"};
    //new String[] {"bool", "int128", "uint256", "address", "bytes", "string", "int", "void", "uint"};
    public static final HashSet<String> BUILTIN_TYPES = new HashSet<>(
            Arrays.asList(BUILTIN_TYPE_NAMES));

    //public static final String ENDORCE_FUNC_NAME = "endorce";
    public static final String LABEL_TOP = "TOP";
    public static final String LABEL_BOTTOM = "any";
    public static final String LABEL_THIS = "this";
    public static final String LABEL_SENDER = "sender";
    public static final String DEAD = "---DEAD---";
    public static final String KEY = "KEY";
    public static final String SHERRLOC_TOP = LABEL_TOP;
    public static final String SHERRLOC_BOTTOM = LABEL_BOTTOM;

    public static final String SHERRLOC_PASS_INDICATOR = "No errors";
    public static final String SHERRLOC_ERROR_INDICATOR = "wrong";
    public static final String TYPECHECK_PASS_MSG = "The program type-checks.";
    public static final String TYPECHECK_ERROR_MSG = "Static type error.";
    public static final String TYPECHECK_NORESULT_MSG = "No result from SHErrLoc.";


    public static final String ADDRESS_TYPE = "address";
    public static final String MAP_TYPE = "map";
    public static final String ARRAY_TYPE = "array";
    public static final String PUBLIC_DECORATOR = "public";
    public static final String PROTECTED_DECORATOR = "protected";
    public static final String FINAL_DECORATOR = "final";
    public static final String PAYABLE_DECORATOR = "payable";

    public static final String TRUSTCENTER_NAME = "trustCenter";
    public static final String SET_CONTRACT_NAME = "Set";
    public static final String PATH_TO_BASECONTRACTCENTRALIZED = "BaseContractCentralized";

    public static final String ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION = "Static reentrancy locks should be maintained except during the last operation";
    public static final String ERROR_MESSAGE_PC_IN_NONLAST_OPERATION = "PC should be maintained except during the last operation";
//    public static final String ERROR_MESSAGE_LOCK_IN_LAST_OPERATION = "The operation at tail position should respect the final reentrancy lock label";

    public static final String DEBUG_UNKNOWN_CONTRACT_NAME = "UNKNOWN";
    public static final String ANONYMOUS_VARIABLE_NAME = "ANONYMOUS";
    public static final String PRINCIPAL_TYPE = "principal";
    public static final String EXCEPTION_ERROR_NAME = "error";
    public static final String METHOD_SEND_NAME = "send";
    public static final String METHOD_BALANCE_NAME = "balance";
    public static final CodeLocation BUILTIN_LOCATION = new CodeLocation(0, 0, "BUILTIN");
    public static final String LABEL_PAYVALUE = "value";
    public static final String BUILT_IN_GAS = "gas";
    public static final String VOID_TYPE = "void";
    public static final String BUILTIN_CONTRACT = "Builtin";

    public static final Map<String, String> BUILTIN_INMETHOD_VARS = generateBuiltInInMethodVars();
    public static final String CONTRACT_KEYWORD = "contract";
    public static final String NOW = "now";

    private static Map<String, String> generateBuiltInInMethodVars() {
        Map<String, String> result = new HashMap<>();
        result.put("now", "uint");
        return result;
    }

    public static final Map<String, String> BUILTIN_GLOBAL_CONSTANTS = generateBuiltInGlobalConstants();

    private static Map<String, String> generateBuiltInGlobalConstants() {
        Map<String, String> result = new HashMap<>();
        // time units
        int sec = 1, min = sec * 60, hour = min * 60, day = hour * 24, week = day * 7;
        result.put("second", String.valueOf(sec));
        result.put("minute", String.valueOf(min));
        result.put("hour", String.valueOf(hour));
        result.put("day", String.valueOf(day));
        result.put("week", String.valueOf(week));
        return result;
    }

    public static final String BASE_CONTRACT_IMP_NAME = "ContractImp";
    public static final List<String> BUILTIN_FILENAMES = Arrays.asList(
            "builtin_files/Contract.scif",
            "builtin_files/ContractImp.scif",
            "builtin_files/ManagedContract.scif",
            "builtin_files/ManagedContractImp.scif",
            "builtin_files/ExternallyManagedContract.scif",
            "builtin_files/ExternallyManagedContractImp.scif",
            "builtin_files/LockManager.scif",
            "builtin_files/TrustManager.scif"
    );
    public static final Iterable<? extends File> BUILTIN_FILES = generateBuiltInFiles();
    public static final String CONSTRUCTOR_KEYWORD = "constructor";
    public static final String SUPER_KEYWORD = "super";
    public static final String RESULT_VARNAME = "result";
    public static final String UINTMAX = "UINT_MAX";
    public static final String UINT_TYPE = "uint";
    private static final String SUPER_PREFIX = "$super";

    /**
     * Create and return actual temporary File objects from the Gradle resources directory
     * Gradle resources are not available as regular files when run in a JAR or outside the repository
     * We create temporary files because downstream code relies heavily on real files and filepaths.
     */
    private static Iterable<? extends File> generateBuiltInFiles() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<File> files = new ArrayList<>();

        try {
            File tempDir = Files.createTempDirectory("builtin-files").toFile();
            tempDir.deleteOnExit();
            for (String resourcePath : BUILTIN_FILENAMES) {
                Path destPath = tempDir.toPath().resolve(resourcePath);
                Files.createDirectories(destPath.getParent()); // create parent directories if needed
                try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                    if (in == null) throw new IllegalStateException("Missing resource: " + resourcePath);
                    Files.copy(in, destPath);
                }

                File destFile = destPath.toFile();
                destFile.deleteOnExit();
                files.add(destFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate built in files.", e);
        }

        return files;
    }

    public static boolean isPrimitiveType(String x) {
        return BUILTIN_TYPES.contains(x);
    }

    /*public static IfConstraint genCons(String from, String to, CodeLocation location) {
        // right flows to left
        return new IfConstraint("<=", from, to, location);
    }*/
    /*public static IfConstraint genNewlineCons() {
        return new IfConstraint();
    }*/

    public static String getLabelNamePc(String location) {
        if (location == null) {
            return "PC";
        } else {
            return location + "." + "PC";
        }
    }

    public static String getLabelNameLock(String location) {
        if (location == null) {
            return "LK";
        } else {
            return location + "." + "LK";
        }
        /*if (prefix.equals("")) {
            return "LK";
        } else {
            return prefix + ".." + "LK";
        }*/
    }

    public static String getLabelNameInLock(String funcFullName) {
        if (funcFullName == null) {
            return "ILK";
        } else {
            return funcFullName + "." + "ILK";
        }
    }

    /*public static String getLabelNameFuncCallPcBefore(String funcName) {
        return funcName + "." + "call.pc.bfr";
    }*/

//    public static String getLabelNameFuncCallPcAfter(String funcName) {
//        return funcName + "." + "call.pc.aft";
//    }

//    public static String getLabelNameCallPcEnd(String funcName) {
//        return funcName + "." + "call.pc.end";
//    }

    public static String getLabelNameFuncCallLock(String funcName) {
        return funcName + "." + "call.lk";
    }

//    public static String getLabelNameFuncCallGamma(String funcName) {
//        return funcName + "." + "gamma.lk";
//    }

    /*public static String getLabelNameFuncCallBefore(String funcName) {
        return funcName + ".." + "call.before";
    }
    public static String getLabelNameFuncCallAfter(String funcName) {
        return funcName + ".." + "call.after";
    }*/
    public static String getLabelNameFuncRtnValue(String funcName) {
        return funcName + "." + "rtn.v";
    }

    public static String getLabelNameFuncRtnLock(String funcName) {
        return funcName + "." + "rtn.lk";
    }

    public static String getLabelNameFuncRtnPc(String funcName) {
        return funcName + "." + "rtn.pc";
    }

    public static String getLabelNameArgLabel(String funcName, VarSym arg) {
        return funcName + "." + arg.getName() + ".lbl";
    }

    public static String getLabelNameFuncExpLabel(String funcName, String name) {
        return funcName + "." + name + ".lbl";
    }

    public static sherrloc.diagnostic.DiagnosticConstraintResult runSherrloc(String consFilePath)
            throws Exception {
        // logger.debug("runSherrloc()...");
        String[] args = new String[]{"-c", consFilePath};
        DiagnosticOptions options = new DiagnosticOptions(args);
        ErrorDiagnosis ana = ErrorDiagnosis.getAnalysisInstance(options);

        sherrloc.diagnostic.DiagnosticConstraintResult result = ana.getConstraintResult();
        return result;

        //sherrloc.diagnostic.ErrorDiagnosis diagnosis = new sherrloc.diagnostic.ErrorDiagnosis();
//        String[] command = new String[] {"bash", "-c", path + "./sherrloc/sherrloc -c " + consFilePath};
//        ProcessBuilder pb = new ProcessBuilder(command);
//        pb.inheritIO();
//        Process p = pb.start();
//        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
//        ArrayList<String> list = new ArrayList<>();
//        String tmp;
//        while ((tmp = br.readLine()) != null) {
//            list.add(tmp);
//            //System.err.println(tmp);
//        }
//        p.waitFor();
//        // logger.debug("finished run SLC, collecting output...");
//        p.destroy();
//        br.close();
//        return list.toArray(new String[0]);
    }
    public static String[] runSLCCMD(String path, String consFilePath)
            throws Exception {

//        sherrloc.diagnostic.ErrorDiagnosis diagnosis = new sherrloc.diagnostic.ErrorDiagnosis();
        String[] command = new String[] {"bash", "-c", path + "/../../../../sherrloc/sherrloc -c " + consFilePath};
        System.err.println(Arrays.toString(command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        ArrayList<String> list = new ArrayList<>();
        String tmp;
        while ((tmp = br.readLine()) != null) {
            list.add(tmp);
            //System.err.println(tmp);
        }
        p.waitFor();
        // logger.debug("finished run SLC, collecting output...");
        p.destroy();
        br.close();
        return list.toArray(new String[0]);
    }

    public static String joinLabels(String lhs, String rhs) {
        return "(" + lhs + " ⊔ " + rhs + ")";
    }

    public static String meetLabels(String lhs, String rhs) {
        return "(" + lhs + " ⊓ " + rhs + ")";
    }

    public static String BuiltinType2ID(BuiltInT type) {
        if (type == BuiltInT.UINT) {
            return "uint";
        } else if (type == BuiltInT.BOOL) {
            return "bool";
        } else if (type == BuiltInT.STRING) {
            return "string";
        } else if (type == BuiltInT.VOID) {
            return "void";
        } else if (type == BuiltInT.ADDRESS) {
            return "address";
        } else if (type == BuiltInT.BYTES) {
            return "bytes";
        } else if (type == BuiltInT.PRINCIPAL) {
            return "principal";
        } else {
            return "unknownT";
        }
    }

    public static boolean writeCons2File(Set<? extends Sym> constructors, List<Constraint> assumptions,
                                         List<Constraint> constraints, File outputFile, boolean isIFC, InterfaceSym contractSym) {
        try {
            // transform every "this" to "contractName.this"
            BufferedWriter consFile = new BufferedWriter(new FileWriter(outputFile));
            if (constraints.size() == 0) {
                return false;
            }
            // logger.debug("Writing the constraints of size {}", constraints.size());
            //System.err.println("Writing the constraints of size " + env.cons.size());
//            VarSym bot = new VarSym();
//            if (!constructors.contains("BOT") && isIFC) {
//                constructors.add("BOT");
//            }
//            if (!constructors.contains("TOP") && isIFC) {
//                constructors.add("TOP");
//            }
            /*if (!constructors.contains("this") && isIFC) {
                constructors.add("this");
            }*/

            Sym bot = null, top = null;
            if (!constructors.isEmpty()) {
                for (Sym principal : constructors) {
                    if (principal.getName().equals(LABEL_BOTTOM)) {
                        bot = principal;
                    }
                    if (principal.getName().equals(LABEL_TOP)) {
                        top = principal;
                    }
                    consFile.write("CONSTRUCTOR " + principal.toSHErrLocFmt() + " 0\n");
                }
            }
            if (!assumptions.isEmpty() || isIFC) {
                consFile.write("%%\n");
                assert bot != null;
                assert top != null;
                if (isIFC) {
                    for (Sym x : constructors) {
                        if (!x.equals(bot) && !x.equals(top)) {
                            consFile.write(bot.toSHErrLocFmt() + " >= " + x.toSHErrLocFmt() + ";" + "\n");
                        }
                        if (!x.equals(top)) {
                            consFile.write(top.toSHErrLocFmt() + " <= " + x.toSHErrLocFmt() + ";" + "\n");
                        }
                    }
                    consFile.write(top.toSHErrLocFmt() + " == " + contractSym.thisSym().toSHErrLocFmt() + ";\n");
                }
//                for (Constraint con : assumptions) {
//                    consFile.write(con.toSherrlocFmt(false) + "\n");
//                }
                consFile.write("%%\n");
                for (Constraint con : assumptions) {
                    consFile.write(con.toSherrlocFmt(true) + "\n");
                }
            } else {
                consFile.write("\n");
            }
            if (!constraints.isEmpty()) {
                for (Constraint con : constraints) {
                    consFile.write(con.toSherrlocFmt(true) + "\n");
                }
            }
            consFile.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean SLCinput(HashSet<String> constructors, ArrayList<Constraint> assumptions,
                                   ArrayList<Constraint> constraints, boolean isIFC) {
        try {
            sherrloc.constraint.ast.Hypothesis hypothesis = new Hypothesis();
            ArrayList<sherrloc.constraint.ast.Axiom> axioms = new ArrayList<>();
            Set<sherrloc.constraint.ast.Constraint> constraintSet = new HashSet<>();
            // transform every "this" to "contractName.this"
            //BufferedWriter consFile = new BufferedWriter(new FileWriter(outputFile));
            // logger.debug("Writing the constraints of size {}", constraints.size());
            if (constraints.size() == 0) {
                return false;
            }
            //System.err.println("Writing the constraints of size " + env.cons.size());
            if (!constructors.contains(LABEL_BOTTOM) && isIFC) {
                constructors.add(LABEL_BOTTOM);
            }
            if (!constructors.contains(LABEL_TOP) && isIFC) {
                constructors.add(LABEL_TOP);
            }
            if (!constructors.contains(LABEL_THIS) && isIFC) {
                constructors.add(LABEL_THIS);
            }

            if (!constructors.isEmpty()) {
                for (String principal : constructors) {
                    sherrloc.constraint.ast.Constructor constructor = new Constructor(principal, 0,
                            0, Variance.POS, sherrloc.constraint.ast.Position.EmptyPosition());
                    // consFile.write("CONSTRUCTOR " + principal + " 0\n");
                }
            }
            if (!assumptions.isEmpty() || isIFC) {
                //consFile.write("%%\n");
                if (isIFC) {
                    for (String x : constructors) {
                        if (!x.equals(LABEL_BOTTOM) && !x.equals(LABEL_TOP)) {
                            //          consFile.write("BOT" + " >= " + x + ";" + "\n");
                        }
                        if (!x.equals(LABEL_TOP)) {
                            //          consFile.write("TOP" + " <= " + x + ";" + "\n");
                        }
                    }
                }
                for (Constraint con : assumptions) {
                    //  consFile.write(con.toSHErrLocFmt(false) + "\n");
                }
                //consFile.write("%%\n");
            } else {
                // consFile.write("\n");
            }
            if (!constraints.isEmpty()) {
                for (Constraint con : constraints) {
                    //consFile.write(con.toSHErrLocFmt(true) + "\n");
                }
            }
            //consFile.close();
        } catch (Exception e) {
            System.out.println("Unexpected exception:");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    protected static final Logger logger = LogManager.getLogger();

    public static FuncSym getCurrentFuncInfo(NTCEnv env, ScopeContext now) {
        while (!(now.cur() instanceof FunctionDef)) {
            now = now.parent();
        }
        FunctionDef funcNode = (FunctionDef) now.cur();
        Sym sym = env.getCurSym(funcNode.getName());
        return ((FuncSym) sym);
    }

    public static TypeSym getBuiltinTypeInfo(String typeName, SymTab s) {
        return (TypeSym) s.lookup(typeName);
    }

    /*private static ExceptionTypeSym builtin_error_sym() {
        return new ExceptionTypeSym("error", new PrimitiveIfLabel(new Name(LABEL_BOTTOM)), new ArrayList<>());
    }

     */

  /*  public static boolean isBuiltinFunc(String funcName) {
        if (funcName.equals("send") || funcName.equals("setTrust")) {
            return true;
        }
        return false;
    }

   */

 /*   public static String transBuiltinFunc(String funcName, Call call) {
        if (funcName.equals("send")) {
            String recipient = call.getArgAt(0).toSolCode();
            String value = call.getArgAt(1).toSolCode();
            return recipient + ".call{value: " + value + "}(\"\")";
        } else if (funcName.equals("setTrust")) {
            String trustee = call.getArgAt(0).toSolCode();
            return funcName + "(" + trustee + ")";
        } else {
            return "unknown built-in function";
        }
    }

  */

    public static boolean emptyFile(String outputFileName) {
        File file = new File(outputFileName);
        return file.length() == 0;
    }

    public static boolean arrayExpressionTypeMatch(ArrayList<Expression> x,
                                                   ArrayList<Expression> y) {

        if (!(x == null && y == null)) {
            if (x == null || y == null || x.size() != y.size()) {
                return false;
            }
            int index = 0;
            while (index < x.size()) {
                if (!x.get(index).typeMatch(y.get(index))) {
                    return false;
                }
                ++index;
            }
        }
        return true;
    }

    public static String getLabelNameContract(ScopeContext context) {
        return context.getSHErrLocName() + "." + "codeLbl";
    }

    public static DynamicSystemOption resolveDynamicOption(String dynamicOption) {
        if (dynamicOption == null) {
            return DynamicSystemOption.BaseContractCentralized;
        }
        return switch (dynamicOption) {
            case "BaseContractCentralized" -> DynamicSystemOption.BaseContractCentralized;
            case "Decentralized" -> DynamicSystemOption.Decentralized;
            default -> null;
        };
        //TODO error report
    }

    public static String translateSLCSuggestion(HashMap<String, List<SourceFile>> programMap, String s,
                                                boolean DEBUG) {
        if (s.charAt(0) != '-') {
            return null;
        }
        if (DEBUG) {
            System.err.println(s);
        }

        //if (true) return s;
        int l = s.indexOf('['), r = s.indexOf(']');
        if (l == -1 || s.charAt(l + 1) != '\"') {
            return null;
        }
        ++l;
        String explanation = "";
        while (s.charAt(l + 1) != '\"') {
            ++l;
            explanation += s.charAt(l);
        }
        l += 2;

        if (!Character.isDigit(s.charAt(l + 1))) {
            return null;
        }
        String slin = "", scol = "";
        while (s.charAt(l + 1) != ',') {
            ++l;
            slin = slin + s.charAt(l);
        }
        ++l;
        while (s.charAt(l + 1) != '-') {
            ++l;
            scol = scol + s.charAt(l);
        }
        int lin = Integer.parseInt(slin), col = Integer.parseInt(scol);

        int p = explanation.indexOf('@');
        String contractName = explanation.substring(p + 1);
        explanation = explanation.substring(0, p);
        //System.out.println("position of @:" + p + " " + contractName);
        // SourceFile program = programMap.get(contractName);
        List<SourceFile> programList = programMap.get(contractName);
        SourceFile program = programList.get(0);
        // TODO steph: I don't think this method has ever been used - so I won't fix the programMap mismatch.

        String rtn =
                program.getSourceFileId() + "(" + slin + "," + scol + "): " + explanation + ".\n";
        rtn += program.getSourceCodeLine(lin - 1) + "\n";
        for (int i = 1; i < col; ++i) {
            rtn += " ";
        }
        rtn += '^';

        return rtn;
    }

    public static String SLCSuggestionToString(Map<String, List<SourceFile>> programMap,
                                               sherrloc.diagnostic.explanation.Explanation exp, boolean DEBUG) {
        String s = exp.toConsoleStringWithExp();
        if (DEBUG) {
            System.err.println(s + "#" + exp.getWeight());
        }

        //if (true) return s;
        int l = s.indexOf('['), r = s.indexOf(']');
        if (l == -1 || s.charAt(l + 1) != '\"') {
            // if (DEBUG) System.out.println("no explanation found");
            return null;
        }
        ++l;
        StringBuilder explanation = new StringBuilder();
        while (s.charAt(l + 1) != '\"') {
            ++l;
            explanation.append(s.charAt(l));
        }
        l += 2;

        // if (DEBUG) System.out.println(explanation);
        if (!Character.isDigit(s.charAt(l + 1))) {
            // if (DEBUG) System.out.println("no range digit found");
            return null;
        }
        StringBuilder slin = new StringBuilder();
        StringBuilder scol = new StringBuilder();
        while (s.charAt(l + 1) != ',') {
            ++l;
            slin.append(s.charAt(l));
        }
        ++l;
        while (s.charAt(l + 1) != '-') {
            ++l;
            scol.append(s.charAt(l));
        }

        int lin = Integer.parseInt(slin.toString()), col = Integer.parseInt(scol.toString());

        int p = explanation.toString().indexOf('@');
        String contractName = explanation.substring(p + 1);
        explanation = new StringBuilder(explanation.substring(0, p));
        // if (DEBUG) System.out.println("position of #:" + p + " " + contractName);
        SourceFile program = null; //= programMap.get(contractName);
        for (List<SourceFile> sourceFileList: programMap.values()) {
            for (SourceFile sourceFile: sourceFileList) {
                if (sourceFile.getSourceFilePath().equals(contractName)) {
                    program = sourceFile;
                    break;
                }
            }
            if (program != null) {
                break;
            }
        }

        assert program != null : contractName;

        if (lin == 0 || col == 0) {
            // Built-in or default
            return program.getSourceFilePath() + "\n" + explanation + ".\n";
        }
        StringBuilder rtn =
                new StringBuilder(
                        program.getSourceFilePath() + "(" + slin + "," + scol + "): " + "\n"
                                + explanation + ".\n");
        rtn.append(program.getSourceCodeLine(lin - 1)).append("\n");
        for (int i = 1; i < col; ++i) {
            rtn.append(" ");
        }
        rtn.append('^');

        return rtn.toString();
    }

    public static String ordNumString(int i) {
        if (i == 1) {
            return "1st";
        } else if (i == 2) {
            return "2nd";
        } else if (i == 3) {
            return "3rd";
        } else {
            return i + "th";
        }
    }

    public static void contextFlow(VisitEnv env, Context outContext, Context endContext,
                                   CodeLocation location) {
        env.addTrustConstraint(new Constraint(new Inequality(outContext.lambda, endContext.lambda),
                env.hypothesis(), location, env.curContractSym().getName(),
                "actually maintained lock of final sub-statement must flow to that of parent statement"));
        env.addTrustConstraint(
                new Constraint(new Inequality(outContext.pc, endContext.pc), env.hypothesis(),
                        location, env.curContractSym().getName(),
                        "Normal-termination control flow of final sub-statement must flow to that of parent statement"));
    }

    public static ExceptionTypeSym getNormalPathException() {
        return new ExceptionTypeSym("*n", new ArrayList<>(), globalScopeContext());
    }

    public static PsiUnit joinPsiUnit(PsiUnit u1, PsiUnit u2) {
        return new PsiUnit(joinContext(u1.c, u2.c), u1.catchable && u2.catchable);
    }

    private static Context joinContext(Context c1, Context c2) {
        return new Context(joinLabels(c1.pc, c2.pc), joinLabels(c1.lambda, c2.lambda));
    }

    public static ExceptionTypeSym getReturnPathException() {
        return new ExceptionTypeSym("*r", new ArrayList<>(), globalScopeContext());
    }

    public static void addBuiltInTypes(SymTab symTab) {
        for (BuiltInT t : BuiltInT.values()) {
            String typeName = Utils.BuiltinType2ID(t);
            TypeSym s = new BuiltinTypeSym(typeName);
            try {
                symTab.add(typeName, s);
            } catch (SymTab.AlreadyDefined e) {
                throw new RuntimeException(e); // cannot happen
            }
        }
    }

    public static ScopeContext globalScopeContext() {
        return new ScopeContext(null, null);
    }

    public static LabeledType builtinLabeldType(BuiltInT builtInT) {
        Type typeNode = new Type(BuiltinType2ID(builtInT));
        typeNode.setLoc(CodeLocation.builtinCodeLocation());
        LabeledType labeledTypeNode = new LabeledType(typeNode);
        labeledTypeNode.setLoc(CodeLocation.builtinCodeLocation());
        return labeledTypeNode;
    }

    public static String methodNameHash(String funName, FunctionSig functionSig) {
        return methodNameHash(funName, functionSig.signature());
    }
    public static String methodNameHash(String funName, String plainSignature) {
        // SHA256
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(plainSignature.getBytes(StandardCharsets.UTF_8));

            // Convert byte array into signum representation
            BigInteger number = new BigInteger(1, encodedhash);

            // Convert message digest into hex value
            StringBuilder hexString = new StringBuilder(number.toString(16));

            // Pad with leading zeros
            while (hexString.length() < 64)
            {
                hexString.insert(0, '0');
            }

            return funName + "_" + hexString.toString();
            // return plainSignature;
        } catch (NoSuchAlgorithmException exc) {
            throw new RuntimeException();
        }
    }

    public static String genSuperName(String contractName) {
        return SUPER_PREFIX + contractName;
    }

    public static boolean isSuperConstructor(String name) {
        return name.startsWith(SUPER_PREFIX);
    }

    public static List<String> SLCEntitiesToStrings(Map<String, List<SourceFile>> programMap, Explanation exp, boolean debug) {
        if (debug) {
            System.err.println("#" + exp.getWeight());
        }

        Set<Entity> entities = exp.getEntities();
        List<String> result = new ArrayList<>();
        int index = 0;

        for (Entity entity : entities) {
            ++index;
            StringBuffer locBuffer = new StringBuffer();
            StringBuffer expBuffer = new StringBuffer();
            entity.toConsoleWithExp(locBuffer, expBuffer);

            int infoEndIndex = locBuffer.indexOf("\"", 1);
            String infoString = locBuffer.substring(0, infoEndIndex);
            int rowNoEndIndex = locBuffer.indexOf(",", infoEndIndex);
            int colNoEndIndex = locBuffer.indexOf("-", rowNoEndIndex);
            String srow = locBuffer.substring(infoEndIndex + 2, rowNoEndIndex);
            String scol = locBuffer.substring(rowNoEndIndex + 1, colNoEndIndex);
            int row = Integer.parseInt(srow), col = Integer.parseInt(scol);

            int fileLoc = locBuffer.indexOf("@");
            String contractName = locBuffer.substring(fileLoc + 1, infoEndIndex);
            StringBuilder explanation = new StringBuilder(locBuffer.substring(1, fileLoc));


            // if (DEBUG) System.out.println("position of #:" + p + " " + contractName);
            SourceFile program = null; //= programMap.get(contractName);
            for (List<SourceFile> sourceFileList: programMap.values()) {
                for (SourceFile sourceFile: sourceFileList) {
                    if (sourceFile.getSourceFilePath().equals(contractName)) {
                        program = sourceFile;
                        break;
                    }
                }
                if (program != null) {
                    break;
                }
            }
            if (program == null) continue;
            assert program != null : contractName;


            if (row == 0 || col == 0) {
                // Built-in or default
                result.add(program.getSourceFilePath() + "\n" + explanation + ".\n");
            }
            StringBuilder rtn =
                    new StringBuilder(
                            program.getSourceFileBasename() + ", line " + row + ", column " + scol + ": " + "\n"
                                    + explanation + ".\n");

            rtn.append("Constraint violated: " + expBuffer.substring(0, expBuffer.length() - 2) + ".\n");

            rtn.append(program.getSourceCodeLine(row - 1)).append("\n");
            for (int i = 1; i < col; ++i) {
                rtn.append(" ");
            }
            rtn.append("^");

//            String expString = expBuffer.toString() + (locBuffer.isEmpty() ? "" : ":[" + locBuffer.toString() + "]");
            result.add(rtn.toString());
        }

        return result;
    }

    public static boolean isBuiltInContractName(String contractName) {
        contractName = resolveNameFromPath(contractName);
        for (String builtInContractPath: BUILTIN_FILENAMES) {
            String builtInCOntractName = resolveNameFromPath(builtInContractPath);
//            System.err.println(builtInContractPath);
            if (builtInCOntractName.equals(contractName)) {
                return true;
            }
        }
        return false;
    }

    static String resolveNameFromPath(String path) {
        int lastSlashPos = path.lastIndexOf('/');
        if (lastSlashPos != -1) path = path.substring(lastSlashPos + 1);
        int lastDotPos = path.lastIndexOf('.');
        if (lastSlashPos != -1) path = path.substring(0, lastDotPos);
        return path;
    }

    public static void genSequenceConstraints(VisitEnv env, String prevLambda, String lastPc, String lastLambda, String newPc, CodeLocation location) {
        env.cons.add(new Constraint(new Inequality(lastPc, newPc), env.hypothesis(), location, env.curContractSym().getName(),
                Utils.ERROR_MESSAGE_PC_IN_NONLAST_OPERATION));
        env.cons.add(new Constraint(new Inequality(lastLambda, joinLabels(prevLambda, newPc)), env.hypothesis(), location, env.curContractSym().getName(),
                Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
    }

    /**
        Generate constraints for s.
        If s is not the last op, prepare and generate the input context for the next op.
     */
    public static Context genNewContextAndConstraints(VisitEnv env, boolean tail_position, Context c, String prevLambda, String newPc, CodeLocation location) {
        env.cons.add(new Constraint(new Inequality(prevLambda, c.lambda), env.hypothesis(), location, env.curContractSym().getName(),
                Utils.ERROR_MESSAGE_LOCK_IN_NONLAST_OPERATION));
        if (tail_position) return c;
        assert c != null: location.errString();
        genSequenceConstraints(env, prevLambda, c.pc, c.lambda, newPc, location);
        return new Context(newPc, c.lambda);
    }

    public static void genConsStmtsWithException(List<Statement> body, VisitEnv env, PathOutcome so, PathOutcome psi, boolean tail_positon) throws SemanticException {
        int index = 0;
        for (Statement s : body) {
            ++index;
            String prevLambda = env.inContext.lambda;
            boolean isTail = index == body.size() && tail_positon;
            so = s.IFCVisit(env, isTail);
            psi.joinExe(so);
            // env.inContext = new Context(so.getNormalPath().c.pc, beginContext.lambda);
//            boolean nextIsTail = index + 1 == body.size() && tail_positon;
            env.inContext = Utils.genNewContextAndConstraints(env, isTail, so.getNormalPath().c, prevLambda, s.nextPcSHL(), s.location());
//                    new Context(so.getNormalPath().c);

        }
    }
    public static void genConsStmts(List<Statement> body, VisitEnv env, PathOutcome so, boolean tail_posn) throws SemanticException {
        int index = 0;
        for (Statement s : body) {
            ++index;
            String prevLambda = env.inContext.lambda;
            boolean isTail = index == body.size() && tail_posn;
            so = s.IFCVisit(env, isTail);
            PsiUnit normalUnit = so.getNormalPath();
            if (normalUnit == null) {
                break;
            }
            // env.inContext = new Context(so.getNormalPath().c.pc, beginContext.lambda);
//            boolean nextIsTail = (index + 1 == body.size() && tail_positon);
            env.inContext = Utils.genNewContextAndConstraints(env, isTail, so.getNormalPath().c, prevLambda, s.nextPcSHL(), s.location());
//                    new Context(so.getNormalPath().c);

        }
    }


    static int inferredLabelCount = 0;
    public static IfLabel newInferredLabel() {
        return new PrimitiveIfLabel(new Name("__inferredLabel" + inferredLabelCount));
    }
}
