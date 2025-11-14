package br.cefet.segaudit.AIContextManager.IO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.core.io.Resource;

public class FileUtil {

  /**
   * Lê todo o conteúdo de um arquivo e o retorna como uma única String.
   *
   * @param filePath O caminho para o arquivo.
   * @return O conteúdo do arquivo como uma String.
   * @throws IOException Se ocorrer um erro de I/O ao ler o arquivo.
   */
  public static String readFileAsString(String filePath) throws IOException {
    return Files.readString(Paths.get(filePath));
  }

  /** Reads the entire content of a classpath resource and returns it as a single String. */
  public static String readResourceAsString(Resource resource) throws IOException {
    try (InputStream inputStream = resource.getInputStream()) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
