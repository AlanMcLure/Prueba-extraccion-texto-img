package org.example.extractor;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Extractor {
    public static void main(String[] args) {
        File imagen = new File("ruta/a/tu/imagen.jpg");

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("ruta/a/tessdata"); // Carpeta donde están los archivos .traineddata
        tesseract.setLanguage("spa"); // Español

        try {
            String texto = tesseract.doOCR(imagen);

            // Buscar NIF o NIE
            Pattern patron = Pattern.compile("\\b(?:[0-9]{8}[A-Z]|[A-Z][0-9]{7}[A-Z])\\b");
            Matcher matcher = patron.matcher(texto);

            while (matcher.find()) {
                System.out.println("NIF encontrado: " + matcher.group());
            }
        } catch (TesseractException e) {
            System.err.println("Error de OCR: " + e.getMessage());
        }
    }
}
