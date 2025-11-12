package br.cefet.segaudit.AIContextManager.IO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
}
