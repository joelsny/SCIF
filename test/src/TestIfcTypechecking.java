import static org.junit.jupiter.api.Assertions.assertNotNull;

import ast.SourceFile;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java_cup.runtime.Symbol;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestIfcTypechecking {
    boolean m_debug = true;

    @ParameterizedTest
    @ValueSource(strings = {
            "applications/Uniswap_ERC20_noe",
            "applications/Uniswap_ERC777_noe",
            "applications/EthCrossChainManager",
            "applications/SysEscrow",
            "basic/Seq",
            "applications/HODLWallet",
            "applications/Dexible",
            "applications/Uniswap_ERC20",
            "applications/ERC20_depmap",
            "applications/KoET",
            "basic/StructEx01",
            "ifcTypechecking/Wallet_lock_exception",
            "applications/ERC20_nodepmap",
            "basic/DependentMap",
            "basic/EmptyContract",
            "basic/EmptyContract2",
            "basic/ExceptionThrowAndCatch",
            "basic/EndroseIf",
            "examples/SimpleStorage",
            // "examples/ERC20",
            "examples/DeployToken",
    })
    void testPositive(String contractName) {
        File logDir = new File("./.scif");
        logDir.mkdirs();
        String inputFilePath = contractName + ".scif";
        URL input = ClassLoader.getSystemResource(inputFilePath);
        System.out.println(inputFilePath + ": " + input);
//        File ntcConsFile = new File(logDir, "ntc.cons");
        ArrayList<File> files = new ArrayList<>();
        files.add(new File(input.getFile()));
        try {
            List<SourceFile> roots = Preprocessor.preprocess(files);
            assertNotNull(roots);
            assert (TypeChecker.regularTypecheck(roots, logDir, m_debug));

            // System.out.println("["+ outputFileName + "]");
            //        ArrayList<File> ifcConsFiles = new ArrayList<>();
            //        for (int i = 0; i < roots.size(); ++i) {
            //            File IFCConsFile;
            //            IFCConsFile = new File(logDir, "ifc" + i + ".cons");
            //            ifcConsFiles.add(IFCConsFile);
            //        }

            System.out.println("\nInformation Flow Typechecking:");

            assert (TypeChecker.ifcTypecheck(roots, logDir, m_debug));
            // System.out.println("["+ outputFileName + "]" + "Information Flow Typechecking finished");
            // logger.debug("running SHErrLoc...");
            // boolean passIFC = runSLC(outputFileName);
        } catch (Exception e) {
            e.printStackTrace();
            assert false;
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "basic/StructEx_W02",
            "applications/Dexible_raw",
            "applications/KoET_raw",
            "ifcTypechecking/DependentMap_W01",
//            "applications/Uniswap_ERC20_raw",
            "applications/ERC20_raw",
            "ifcTypechecking/WEx1",
//            "ifcTypechecking/LocalTrust_W01",
            "ifcTypechecking/Wallet_lock_exception_W01",
            "ifcTypechecking/Wallet_lock_exception_W02",
            "ifcTypechecking/TailCall",
            // "examples/ERC20",
    })
    void testNegative(String contractName) {
        File logDir = new File("./.scif");
        logDir.mkdirs();
        String inputFilePath = contractName + ".scif";
        URL input = ClassLoader.getSystemResource(inputFilePath);
        System.out.println(inputFilePath + ": " + input);
//        File ntcConsFile = new File(logDir, "ntc.cons");
        ArrayList<File> files = new ArrayList<>();
        files.add(new File(input.getFile()));
        try {
            List<SourceFile> roots = Preprocessor.preprocess(files);
            assertNotNull(roots);
            assert (TypeChecker.regularTypecheck(roots, logDir, m_debug));

            // System.out.println("["+ outputFileName + "]");
            //        ArrayList<File> ifcConsFiles = new ArrayList<>();
            //        for (int i = 0; i < roots.size(); ++i) {
            //            File IFCConsFile;
            //            IFCConsFile = new File(logDir, "ifc" + i + ".cons");
            //            ifcConsFiles.add(IFCConsFile);
            //        }

            System.out.println("\nInformation Flow Typechecking:");

            assert (!TypeChecker.ifcTypecheck(roots, logDir, m_debug));
            // System.out.println("["+ outputFileName + "]" + "Information Flow Typechecking finished");
            // logger.debug("running SHErrLoc...");
            // boolean passIFC = runSLC(outputFileName);
        } catch (Exception e) {
            e.printStackTrace();
            assert true;
        }
    }
}
