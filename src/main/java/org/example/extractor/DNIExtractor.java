package org.example.extractor;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DNIExtractor {

    private static final Pattern NIF_PATTERN = Pattern.compile("\\b[0-9]{8}[A-Z]\\b");
    private static final Pattern NIE_PATTERN = Pattern.compile("\\b[XYZ][0-9]{7}[A-Z]\\b");
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("(?i)nombre[s]?:?\\s*([A-ZÁÉÍÓÚÑ\\s]+)");
    private static final Pattern APELLIDOS_PATTERN = Pattern.compile("(?i)apellidos?:?\\s*([A-ZÁÉÍÓÚÑ\\s]+)");

    public static void main(String[] args) {
        DNIExtractor extractor = new DNIExtractor();
        extractor.procesarDNI();
    }

    public void procesarDNI() {
        try {
            // Configurar Tesseract
            Tesseract tesseract = configurarTesseract();

            // Cargar imagen
            BufferedImage imagen = cargarImagen();
            if (imagen == null) {
                System.err.println("No se pudo cargar la imagen");
                return;
            }

            // Preprocesar imagen para mejorar OCR
            BufferedImage imagenProcesada = preprocesarImagen(imagen);

            // Extraer texto
            String textoCompleto = tesseract.doOCR(imagenProcesada);
            System.out.println("=== TEXTO EXTRAÍDO ===");
            System.out.println(textoCompleto);
            System.out.println("=====================");

            // Extraer información específica
            extraerInformacionDNI(textoCompleto);

        } catch (Exception e) {
            System.err.println("Error procesando DNI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Tesseract configurarTesseract() {
        Tesseract tesseract = new Tesseract();

        try {
            // Intentar usar tessdata del classpath
            ClassLoader classLoader = getClass().getClassLoader();
            URL tessdataURL = classLoader.getResource("tessdata");

            if (tessdataURL != null) {
                String tessdataPath = new File(tessdataURL.getFile()).getAbsolutePath();
                tesseract.setDatapath(tessdataPath);
            } else {
                // Usar tessdata por defecto
                File tessDataFolder = LoadLibs.extractTessResources("tessdata");
                tesseract.setDatapath(tessDataFolder.getAbsolutePath());
            }

            // Configuraciones para mejorar OCR en documentos españoles
            tesseract.setLanguage("spa+eng"); // Español e inglés
            tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
            tesseract.setOcrEngineMode(1); // Neural nets LSTM engine only

            // Variables de configuración adicionales
            tesseract.setTessVariable("tessedit_char_whitelist",
                    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÉÍÓÚÑáéíóúñ :.-/");

        } catch (Exception e) {
            System.err.println("Error configurando Tesseract: " + e.getMessage());
        }

        return tesseract;
    }

    private BufferedImage cargarImagen() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL imagenURL = classLoader.getResource("imagenes/dni.png");

            if (imagenURL == null) {
                System.err.println("No se encontró la imagen en resources/imagenes/dni.png");
                return null;
            }

            return ImageIO.read(imagenURL);

        } catch (IOException e) {
            System.err.println("Error cargando imagen: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage preprocesarImagen(BufferedImage original) {
        // Convertir a escala de grises
        BufferedImage gris = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gris.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();

        // Aumentar contraste y nitidez
        BufferedImage procesada = aumentarContraste(gris);

        // Escalar imagen si es muy pequeña
        if (procesada.getWidth() < 800) {
            procesada = escalarImagen(procesada, 2.0);
        }

        return procesada;
    }

    private BufferedImage aumentarContraste(BufferedImage imagen) {
        BufferedImage resultado = new BufferedImage(
                imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int rgb = imagen.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;

                // Aumentar contraste
                gray = Math.min(255, Math.max(0, (int)((gray - 128) * 1.5 + 128)));

                int newRgb = (gray << 16) | (gray << 8) | gray;
                resultado.setRGB(x, y, newRgb);
            }
        }

        return resultado;
    }

    private BufferedImage escalarImagen(BufferedImage original, double factor) {
        int nuevoAncho = (int)(original.getWidth() * factor);
        int nuevaAltura = (int)(original.getHeight() * factor);

        BufferedImage escalada = new BufferedImage(nuevoAncho, nuevaAltura, original.getType());
        Graphics2D g2d = escalada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(original, 0, 0, nuevoAncho, nuevaAltura, null);
        g2d.dispose();

        return escalada;
    }

    private void extraerInformacionDNI(String texto) {
        System.out.println("\n=== INFORMACIÓN EXTRAÍDA ===");

        // Limpiar texto
        String textoLimpio = texto.replaceAll("\\s+", " ").trim();

        // Buscar NIF
        List<String> nifs = buscarPatron(textoLimpio, NIF_PATTERN);
        if (!nifs.isEmpty()) {
            System.out.println("NIF encontrado(s):");
            nifs.forEach(nif -> {
                System.out.println("  - " + nif + (validarNIF(nif) ? " (válido)" : " (inválido)"));
            });
        }

        // Buscar NIE
        List<String> nies = buscarPatron(textoLimpio, NIE_PATTERN);
        if (!nies.isEmpty()) {
            System.out.println("NIE encontrado(s):");
            nies.forEach(nie -> {
                System.out.println("  - " + nie + (validarNIE(nie) ? " (válido)" : " (inválido)"));
            });
        }

        // Buscar nombre
        Matcher nombreMatcher = NOMBRE_PATTERN.matcher(textoLimpio);
        if (nombreMatcher.find()) {
            System.out.println("Nombre: " + nombreMatcher.group(1).trim());
        }

        // Buscar apellidos
        Matcher apellidosMatcher = APELLIDOS_PATTERN.matcher(textoLimpio);
        if (apellidosMatcher.find()) {
            System.out.println("Apellidos: " + apellidosMatcher.group(1).trim());
        }

        if (nifs.isEmpty() && nies.isEmpty()) {
            System.out.println("No se encontraron documentos de identidad válidos");
            System.out.println("Texto para debug: " + textoLimpio);
        }
    }

    private List<String> buscarPatron(String texto, Pattern patron) {
        List<String> resultados = new ArrayList<>();
        Matcher matcher = patron.matcher(texto);

        while (matcher.find()) {
            resultados.add(matcher.group());
        }

        return resultados;
    }

    private boolean validarNIF(String nif) {
        if (nif == null || nif.length() != 9) return false;

        try {
            String letras = "TRWAGMYFPDXBNJZSQVHLCKE";
            int numero = Integer.parseInt(nif.substring(0, 8));
            char letraCalculada = letras.charAt(numero % 23);
            char letraProporcionada = nif.charAt(8);

            return letraCalculada == letraProporcionada;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validarNIE(String nie) {
        if (nie == null || nie.length() != 9) return false;

        try {
            String letras = "TRWAGMYFPDXBNJZSQVHLCKE";
            char primeraLetra = nie.charAt(0);

            int prefijo = switch (primeraLetra) {
                case 'X' -> 0;
                case 'Y' -> 1;
                case 'Z' -> 2;
                default -> -1;
            };

            if (prefijo == -1) return false;

            String numeroStr = prefijo + nie.substring(1, 8);
            int numero = Integer.parseInt(numeroStr);
            char letraCalculada = letras.charAt(numero % 23);
            char letraProporcionada = nie.charAt(8);

            return letraCalculada == letraProporcionada;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}