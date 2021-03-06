/*
 Copyright 2006-2011 Abdulla Abdurakhmanov (abdulla@latestbit.com)
 Original sources are available at www.latestbit.com

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.bn.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.plugin.logging.Log;

public class Module {
    private File[] moduleFiles = null;
    private String moduleName;
    private String outputDir;
    private String modulesDir;
    private String targetDir;
    private Log logger;

    public Module(Log logger, String modulesPath, String name, String outputDir, String targetDir)
        throws Exception {
        this.logger = logger;
        this.targetDir = targetDir;
        setModuleName(name);
        setOutputDir(outputDir);
        setModulesPath(modulesPath);
        checkFolders(outputDir);
        loadTransformations();
    }

    private Path checkFolders(String path) throws FileNotFoundException {
        File basePath = new File(path);
        if (!basePath.exists()) {
            boolean ret = basePath.mkdirs();
            if (!ret) {
                throw new FileNotFoundException("Cannot create directory: " + basePath.getPath());
            }
        }
        return basePath.toPath();
    }

    private File createOutputFileForInput(File input) {
        String fileName = input.getName().substring(0, input.getName().lastIndexOf(".")) + "."
                          + getModuleName();
        File result = new File(getOutputDir(), fileName);

        return result;
    }

    private void extractSubDir(URI zipFileUri, String pathInsideZip, final Path targetDir)
        throws IOException {
        FileSystem zipFs = FileSystems.newFileSystem(zipFileUri, Collections.<String, Object> emptyMap());
        final Path pathInZip = zipFs.getPath(pathInsideZip);
        Files.walkFileTree(pathInZip, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                // Make sure that we conserve the hierachy of files and folders inside the zip
                Path relativePathInZip = pathInZip.relativize(filePath);
                Path targetPath = targetDir.resolve(relativePathInZip.toString());
                Files.createDirectories(targetPath.getParent());

                // And extract the file
                Files.copy(filePath, targetPath);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private File retrieveModulesFiles(String path) throws URISyntaxException, IOException {
        logger.debug("retrieveModulesFiles " + path);
        URI uri = this.getClass().getResource(path).toURI();
        logger.debug("uri " + uri.toString());
        Path modulesPath;
        if (uri.getScheme().equals("jar")) {
            File modulesDirFile = new File(targetDir + modulesDir);
            modulesPath = modulesDirFile.toPath();
            extractSubDir(uri, path, modulesPath);
        } else {
            modulesPath = Paths.get(uri);
        }
        logger.debug("modulesPath " + modulesPath.toString());

        return modulesPath.toFile();
    }

    private void loadTransformations() throws Exception {
        logger.debug("modulesPath = " + getModulesPath());
        logger.debug("moduleName = " + getModuleName());

        File basePath = retrieveModulesFiles(getModulesPath() + "/" + getModuleName());

        if (basePath.isDirectory()) {
            moduleFiles = basePath.listFiles();
        } else {
            throw new FileNotFoundException("Modules must be directory!");
        }
    }

    public void translate(InputStream stream) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();

        for (File file : moduleFiles) {
            if (file.isFile()) {
                Transformer transformer = factory.newTransformer(new StreamSource(file));

                transformer.setErrorListener(new ErrorListener() {
                    public void warning(TransformerException exception) {
                        logger.warn("[W] Warning:" + exception);
                    }

                    public void error(TransformerException exception) {
                        logger.error("[!] Error:" + exception);
                    }

                    public void fatalError(TransformerException exception) {
                        logger.error("[!!!] Fatal error:" + exception);
                    }
                });

                File outputFile = createOutputFileForInput(file);

                transformer.transform(new StreamSource(stream), new StreamResult(outputFile));
            }
        }
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModulesPath() {
        return modulesDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    private void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    private void setModulesPath(String modulesPath) {
        this.modulesDir = modulesPath;
    }

    private void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }
}
