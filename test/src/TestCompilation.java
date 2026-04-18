import static org.junit.jupiter.api.Assertions.assertNotNull;

import ast.SourceFile;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestCompilation {
    boolean m_debug = true;

    @ParameterizedTest
    @ValueSource(strings = {
//            "applications/Uniswap_ERC20_noe",
//            "applications/Uniswap_ERC777_noe",
            "basic/StructEx04",
            "basic/StructEx03",
            "basic/StructEx02",
            "basic/StructEx01",
            "applications/EthCrossChainManager",
            "applications/HODLWallet",
            "applications/SysEscrow",
//            "applications/Uniswap_ERC20",
//            "applications/Uniswap_ERC777",
            "applications/Dexible",
            "applications/KoET",
            "applications/ERC20_depmap",
            "applications/ERC20_nodepmap",
            "applications/ERC20_depmap_noe",
//            "examples/DeployToken02", TODO
            "basic/DependentMap",
            "basic/EmptyContract",
            "basic/EmptyContract2",
            "basic/ExceptionThrowAndCatch",
            "basic/EndroseIf",
            "ifcTypechecking/Wallet_lock_exception",
            // "examples/ERC20",
            "examples/SimpleStorage",
            "examples/DeployToken",
            "multiContract/importTest/import1",
            "multiContract/DexibleWithEvents",
    })
    void testPositive(String contractName) {
        File logDir = new File("./.scif");
        logDir.mkdirs();
        String inputFilePath = contractName + ".scif";
        URL input = ClassLoader.getSystemResource(inputFilePath);
        System.out.println(inputFilePath + ": " + input);
//        File ntcConsFile = new File(logDir, "ntc.cons");
        List<File> files = new ArrayList<>();
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

            SourceFile root = null;
            for (SourceFile r: roots) {
                if (r.getSourceFilePath().equals(input.getPath())) {
                    root = r;
                    break;
                }
            }
            assert root != null: input.getPath();

            File outputFile = File.createTempFile("tmp", "sol");
            outputFile.deleteOnExit();
            SolCompiler.compile(List.of(root), outputFile);
        } catch (Exception e) {
            e.printStackTrace();
            assert false;
        }
    }
}
