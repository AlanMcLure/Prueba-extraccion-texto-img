package org.example;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniversalDocumentExtractor {

    // Patrones para diferentes tipos de documentos
    private static final Map<String, Pattern> DOCUMENT_PATTERNS = new HashMap<>();

    static {
        // DNI/NIE
        DOCUMENT_PATTERNS.put("NIF", Pattern.compile("\\b[0-9]{8}[A-Z]\\b"));
        DOCUMENT_PATTERNS.put("NIE", Pattern.compile("\\b[XYZ][0-9]{7}[A-Z]\\b"));

        // Pasaporte
        DOCUMENT_PATTERNS.put("PASAPORTE", Pattern.compile("\\b[A-Z]{3}[0-9]{6}\\b"));

        // Números de cuenta bancaria (IBAN)
        DOCUMENT_PATTERNS.put("IBAN", Pattern.compile("\\bES[0-9]{22}\\b"));

        // Números de la Seguridad Social
        DOCUMENT_PATTERNS.put("SS", Pattern.compile("\\b[0-9]{2}\\s?[0-9]{8}\\s?[0-9]{2}\\b"));

        // Fechas
        DOCUMENT_PATTERNS.put("FECHA", Pattern.compile("\\b[0-3]?[0-9][/-][0-1]?[0-9][/-][0-9]{2,4}\\b"));

        // Códigos postales
        DOCUMENT_PATTERNS.put("CP", Pattern.compile("\\b[0-9]{5}\\b"));

        // Teléfonos
        DOCUMENT_PATTERNS.put("TELEFONO", Pattern.compile("\\b[6-9][0-9]{8}\\b|\\b[0-9]{3}\\s?[0-9]{3}\\s?[0-9]{3}\\b"));

        // Email
        DOCUMENT_PATTERNS.put("EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"));
    }

    // Patrones para campos específicos de documentos
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("(?i)(?:nombre[s]?|name)[:\\s]*([A-ZÁÉÍÓÚÑ][A-Za-záéíóúñ\\s]+)");
    private static final Pattern APELLIDOS_PATTERN = Pattern.compile("(?i)(?:apellidos?|surname)[:\\s]*([A-ZÁÉÍÓÚÑ][A-Za-záéíóúñ\\s]+)");
    private static final Pattern DOMICILIO_PATTERN = Pattern.compile("(?i)(?:domicilio|dirección|address)[:\\s]*([A-Za-záéíóúñ0-9\\s,.-]+)");

    private Tesseract tesseract;

    public static void main(String[] args) {
        UniversalDocumentExtractor extractor = new UniversalDocumentExtractor();

        // Ejemplos de uso
        System.out.println("=== PROCESANDO DNI ===");
        extractor.procesarDocumento("imagenes/dni.png", TipoDocumento.DNI);

        System.out.println("\n=== PROCESANDO PDF ESCANEADO ===");
        extractor.procesarDocumento("documentos/contrato.pdf", TipoDocumento.CONTRATO);

        System.out.println("\n=== PROCESANDO FACTURA ===");
        extractor.procesarDocumento("imagenes/factura.jpg", TipoDocumento.FACTURA);
    }

    public UniversalDocumentExtractor() {
        this.tesseract = configurarTesseract();
    }

    public void procesarDocumento(String rutaArchivo, TipoDocumento tipoDocumento) {
        try {
            List<BufferedImage> imagenes = cargarDocumento(rutaArchivo);

            if (imagenes.isEmpty()) {
                System.err.println("No se pudieron cargar las imágenes del documento: " + rutaArchivo);
                return;
            }

            StringBuilder textoCompleto = new StringBuilder();

            // Procesar cada página/imagen
            for (int i = 0; i < imagenes.size(); i++) {
                System.out.println("Procesando página/imagen " + (i + 1) + " de " + imagenes.size());

                BufferedImage imagenProcesada = preprocesarImagen(imagenes.get(i), tipoDocumento);
                String textoPagina = tesseract.doOCR(imagenProcesada);

                textoCompleto.append("=== PÁGINA ").append(i + 1).append(" ===\n");
                textoCompleto.append(textoPagina).append("\n\n");
            }

            System.out.println("=== TEXTO EXTRAÍDO ===");
            System.out.println(textoCompleto.toString());
            System.out.println("=====================");

            // Extraer información según el tipo de documento
            extraerInformacionEspecifica(textoCompleto.toString(), tipoDocumento);

        } catch (Exception e) {
            System.err.println("Error procesando documento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<BufferedImage> cargarDocumento(String rutaArchivo) throws IOException {
        List<BufferedImage> imagenes = new ArrayList<>();

        ClassLoader classLoader = getClass().getClassLoader();
        URL archivoURL = classLoader.getResource(rutaArchivo);

        if (archivoURL == null) {
            System.err.println("No se encontró el archivo: " + rutaArchivo);
            return imagenes;
        }

        File archivo = new File(archivoURL.getFile());
        String extension = obtenerExtension(archivo.getName()).toLowerCase();

        switch (extension) {
            case "pdf":
                imagenes = convertirPDFAImagenes(archivo);
                break;
            case "png", "jpg", "jpeg", "tiff", "bmp":
                BufferedImage imagen = ImageIO.read(archivo);
                if (imagen != null) {
                    imagenes.add(imagen);
                }
                break;
            default:
                System.err.println("Formato de archivo no soportado: " + extension);
        }

        return imagenes;
    }

    private List<BufferedImage> convertirPDFAImagenes(File archivoPDF) throws IOException {
        List<BufferedImage> imagenes = new ArrayList<>();

        try (PDDocument documento = PDDocument.load(archivoPDF)) {
            PDFRenderer renderer = new PDFRenderer(documento);

            for (int pagina = 0; pagina < documento.getNumberOfPages(); pagina++) {
                // Renderizar a 300 DPI para mejor calidad OCR
                BufferedImage imagen = renderer.renderImageWithDPI(pagina, 300, ImageType.RGB);
                imagenes.add(imagen);
            }
        }

        return imagenes;
    }

    private Tesseract configurarTesseract() {
        Tesseract tess = new Tesseract();

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL tessdataURL = classLoader.getResource("tessdata");

            if (tessdataURL != null) {
                String tessdataPath = new File(tessdataURL.getFile()).getAbsolutePath();
                tess.setDatapath(tessdataPath);
            } else {
                File tessDataFolder = LoadLibs.extractTessResources("tessdata");
                tess.setDatapath(tessDataFolder.getAbsolutePath());
            }

            tess.setLanguage("spa+eng");
            tess.setPageSegMode(1);
            tess.setOcrEngineMode(1);

        } catch (Exception e) {
            System.err.println("Error configurando Tesseract: " + e.getMessage());
        }

        return tess;
    }

    private BufferedImage preprocesarImagen(BufferedImage original, TipoDocumento tipoDocumento) {
        // Preprocesamiento específico según el tipo de documento
        BufferedImage procesada = original;

        switch (tipoDocumento) {
            case DNI, PASAPORTE:
                // Documentos oficiales: mayor contraste y nitidez
                procesada = convertirAGris(procesada);
                procesada = aumentarContraste(procesada, 1.8);
                procesada = aplicarFiltroNitidez(procesada);
                break;

            case FACTURA, CONTRATO:
                // Documentos comerciales: menos agresivo
                procesada = convertirAGris(procesada);
                procesada = aumentarContraste(procesada, 1.3);
                break;

            case DOCUMENTO_MEDICO:
                // Documentos médicos: preservar detalles
                procesada = convertirAGris(procesada);
                procesada = aumentarContraste(procesada, 1.5);
                procesada = reducirRuido(procesada);
                break;
        }

        // Escalar si es necesario
        if (procesada.getWidth() < 1000) {
            procesada = escalarImagen(procesada, 2.0);
        }

        return procesada;
    }

    private BufferedImage convertirAGris(BufferedImage original) {
        BufferedImage gris = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gris.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        return gris;
    }

    private BufferedImage aumentarContraste(BufferedImage imagen, double factor) {
        BufferedImage resultado = new BufferedImage(
                imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int rgb = imagen.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;

                gray = Math.min(255, Math.max(0, (int)((gray - 128) * factor + 128)));

                int newRgb = (gray << 16) | (gray << 8) | gray;
                resultado.setRGB(x, y, newRgb);
            }
        }

        return resultado;
    }

    private BufferedImage aplicarFiltroNitidez(BufferedImage imagen) {
        // Filtro de nitidez simple
        float[] filtro = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };

        return aplicarFiltroConvolucion(imagen, filtro, 3);
    }

    private BufferedImage reducirRuido(BufferedImage imagen) {
        // Filtro gaussiano para reducir ruido
        float[] filtro = {
                1/16f, 2/16f, 1/16f,
                2/16f, 4/16f, 2/16f,
                1/16f, 2/16f, 1/16f
        };

        return aplicarFiltroConvolucion(imagen, filtro, 3);
    }

    private BufferedImage aplicarFiltroConvolucion(BufferedImage imagen, float[] filtro, int tamaño) {
        BufferedImage resultado = new BufferedImage(
                imagen.getWidth(), imagen.getHeight(), imagen.getType());

        int offset = tamaño / 2;

        for (int y = offset; y < imagen.getHeight() - offset; y++) {
            for (int x = offset; x < imagen.getWidth() - offset; x++) {
                float suma = 0;

                for (int fy = 0; fy < tamaño; fy++) {
                    for (int fx = 0; fx < tamaño; fx++) {
                        int px = x + fx - offset;
                        int py = y + fy - offset;
                        int rgb = imagen.getRGB(px, py);
                        int gray = (rgb >> 16) & 0xFF;
                        suma += gray * filtro[fy * tamaño + fx];
                    }
                }

                int valorFinal = Math.min(255, Math.max(0, (int)suma));
                int newRgb = (valorFinal << 16) | (valorFinal << 8) | valorFinal;
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

    private void extraerInformacionEspecifica(String texto, TipoDocumento tipoDocumento) {
        System.out.println("\n=== INFORMACIÓN EXTRAÍDA ===");

        String textoLimpio = texto.replaceAll("\\s+", " ").trim();

        // Extraer patrones comunes
        for (Map.Entry<String, Pattern> entrada : DOCUMENT_PATTERNS.entrySet()) {
            List<String> coincidencias = buscarPatron(textoLimpio, entrada.getValue());
            if (!coincidencias.isEmpty()) {
                System.out.println(entrada.getKey() + " encontrado(s):");
                coincidencias.forEach(coincidencia -> System.out.println("  - " + coincidencia));
            }
        }

        // Extraer campos específicos según el tipo
        switch (tipoDocumento) {
            case DNI, PASAPORTE:
                extraerDatosPersonales(textoLimpio);
                break;
            case FACTURA:
                extraerDatosFactura(textoLimpio);
                break;
            case CONTRATO:
                extraerDatosContrato(textoLimpio);
                break;
            case DOCUMENTO_MEDICO:
                extraerDatosMedicos(textoLimpio);
                break;
        }
    }

    private void extraerDatosPersonales(String texto) {
        extraerCampo(texto, NOMBRE_PATTERN, "Nombre");
        extraerCampo(texto, APELLIDOS_PATTERN, "Apellidos");
        extraerCampo(texto, DOMICILIO_PATTERN, "Domicilio");
    }

    private void extraerDatosFactura(String texto) {
        Pattern importePattern = Pattern.compile("(?i)(?:total|importe)[:\\s]*([0-9]+[,.]?[0-9]*)[\\s€]");
        Pattern fechaFacturaPattern = Pattern.compile("(?i)(?:fecha|date)[:\\s]*([0-3]?[0-9][/-][0-1]?[0-9][/-][0-9]{2,4})");

        extraerCampo(texto, importePattern, "Importe");
        extraerCampo(texto, fechaFacturaPattern, "Fecha factura");
    }

    private void extraerDatosContrato(String texto) {
        Pattern clausulaPattern = Pattern.compile("(?i)cláusula[\\s]*([0-9]+)");
        Pattern vigenciaPattern = Pattern.compile("(?i)(?:vigencia|validez)[:\\s]*([^.]+)");

        extraerCampo(texto, clausulaPattern, "Cláusulas");
        extraerCampo(texto, vigenciaPattern, "Vigencia");
    }

    private void extraerDatosMedicos(String texto) {
        Pattern diagnosticoPattern = Pattern.compile("(?i)(?:diagnóstico|diagnosis)[:\\s]*([^.]+)");
        Pattern medicamentoPattern = Pattern.compile("(?i)(?:medicamento|tratamiento)[:\\s]*([^.]+)");

        extraerCampo(texto, diagnosticoPattern, "Diagnóstico");
        extraerCampo(texto, medicamentoPattern, "Medicamento/Tratamiento");
    }

    private void extraerCampo(String texto, Pattern patron, String nombreCampo) {
        Matcher matcher = patron.matcher(texto);
        if (matcher.find()) {
            System.out.println(nombreCampo + ": " + matcher.group(1).trim());
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

    private String obtenerExtension(String nombreArchivo) {
        int ultimoPunto = nombreArchivo.lastIndexOf('.');
        return ultimoPunto > 0 ? nombreArchivo.substring(ultimoPunto + 1) : "";
    }

    public enum TipoDocumento {
        DNI,
        PASAPORTE,
        FACTURA,
        CONTRATO,
        DOCUMENTO_MEDICO
    }
}