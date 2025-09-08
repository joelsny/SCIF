import java.io.*;

import ast.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java_cup.runtime.*;

import typecheck.exceptions.SemanticException;
import typecheck.*;
import parser.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Preprocessor {
    /**
     * parse all SCIF source files, resolve dependency and imports, and store AST in roots in topo order.
     */
    public static List<SourceFile> preprocess(List<File> inputFiles) throws IOException, SemanticException, Parser.SyntaxError {
        List<SourceFile> roots = new ArrayList<>();
        Queue<File> mentionedFiles = new ArrayDeque<>(inputFiles);
        InheritGraph graph = new InheritGraph();
        Map<String, List<SourceFile>> fileMap = new HashMap<>();
        Set<String> includedFilePaths = inputFiles.stream().flatMap(file -> Stream.of(file.getAbsolutePath())).collect(
                Collectors.toSet());

        // add all built-in source files
        for (File builtinFile : Utils.BUILTIN_FILES) {
            Symbol result = Parser.parse(builtinFile, null); //p.parse()
            assert result != null;

            List<SourceFile> rootsFiles = (List<SourceFile>) result.value;
            assert rootsFiles.size() == 1;
            SourceFile root = (rootsFiles.getFirst()).makeBuiltIn();
            if (root instanceof ContractFile) {
                ((ContractFile) root).getContract().clearExtends();
            }

        // TODO(Siqiu) root.setName(inputFile.name());

            List<String> sourceCode = Files.readAllLines(Paths.get(builtinFile.getAbsolutePath()),
                    StandardCharsets.UTF_8);
            root.setSourceCode(sourceCode);

            root.addBuiltIns();
            roots.add(root);
            assert root.ntcAddImportEdges(graph);
            includedFilePaths.add(builtinFile.getAbsolutePath());
            fileMap.put(builtinFile.getAbsolutePath(), new ArrayList<>(List.of(root)));
        }

        // add all contracts from inputFiles recursively
        while (!mentionedFiles.isEmpty()) {
            File file = mentionedFiles.poll();
            Symbol result = Parser.parse(file, null); //p.parse()
            assert result != null;

            List<SourceFile> rootsFiles = (List<SourceFile>) result.value;
            assert !rootsFiles.isEmpty();

            // TODO(Siqiu) root.setName(inputFile.name());
            List<String> fullSourceCode = Files.readAllLines(Paths.get(file.getAbsolutePath()),
                    StandardCharsets.UTF_8);

            for (SourceFile root : rootsFiles) {
                root.setSourceCode(fullSourceCode);
            // since sourceCode only used to show SLC error msgs, need to keep full length to show correct line num
                root.addBuiltIns();
                roots.add(root);
                assert root.ntcAddImportEdges(graph);

                for (String filePath : root.importPaths()) {
                    // only for those imported file paths != current file path
                    if (!includedFilePaths.contains(filePath)) {
                        mentionedFiles.add(new File(filePath));
                        includedFilePaths.add(filePath);
                    }
                }
            }

            fileMap.put((rootsFiles.getFirst()).getSourceFilePath(), rootsFiles);
        }

        // TODO(steph) do we need to check if there's any non-existent contract name? see previous commits.

        // construct sourceFileMap: file path -> list of AST contract/interface
        Map<String, List<TopLayerNode>> sourceFileMap = new HashMap<>();
        for (SourceFile root : roots) {
            if (root instanceof ContractFile) {
                sourceFileMap.computeIfAbsent(root.getSourceFilePath(), k -> new ArrayList<>()).add(((ContractFile) root).getContract());
            } else if (root instanceof InterfaceFile) {
                sourceFileMap.computeIfAbsent(root.getSourceFilePath(), k -> new ArrayList<>()).add(((InterfaceFile) root).getInterface());
            } else {
                assert false : "Unreachable case: " + root.getContractName();
            }
        }

        // resolve dependency
        List<SourceFile> toporder = new ArrayList<>();
            for (String x : graph.getTopologicalQueue()) {
        // code-paste in a topological order
            List<SourceFile> rootsFile = fileMap.get(x);
            if (rootsFile == null || rootsFile.isEmpty()) {
                assert false;
                return null;
            }

            for (SourceFile rt : rootsFile) {
                // order of contracts/interfaces under one file path is preserved
                toporder.add(rt);
                rt.updateImports(fileMap);
                rt.codePasteContract(x, sourceFileMap);
            }
        }

        return toporder;
    }
}