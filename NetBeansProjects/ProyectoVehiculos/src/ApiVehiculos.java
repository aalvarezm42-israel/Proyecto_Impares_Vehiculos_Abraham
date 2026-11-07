import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ApiVehiculos {

    public static void main(String[] args) throws IOException {
        // Puerto 8081 para no chocar con nada más
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/api/vehiculos", new VehiculoController());
        server.setExecutor(null);
        server.start();
        System.out.println("API Vehículos iniciada en http://localhost:8081/api/vehiculos");
    }

    // 1. MODELO
    static class Vehiculo {
        public int id;
        public String marca;
        public String modelo;
        public int anio;
        public double precio;
        public String color;
    }

    // 2. SERVICIO / BASE DE DATOS
    static class VehiculoService {
        private static final String URL = "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        private static final String USER = "SYSTEM";
        private static final String PASS = "admin";

        public String registrarVehiculo(Vehiculo v, String jsonOriginal) {
            String resultado = "Éxito";
            try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
                String sql = "INSERT INTO VEHICULOS (ID, MARCA, MODELO, ANIO, PRECIO, COLOR) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, v.id); 
                    ps.setString(2, v.marca); 
                    ps.setString(3, v.modelo);
                    ps.setInt(4, v.anio); 
                    ps.setDouble(5, v.precio); 
                    ps.setString(6, v.color);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                resultado = "Error DB: " + e.getMessage();
            } finally {
                registrarBitacora("POST /api/vehiculos", jsonOriginal, resultado);
            }
            return resultado;
        }

        private void registrarBitacora(String operacion, String datos, String resultado) {
            try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
                // AQUI ESTA EL CAMBIO QUE HICIMOS EN DBeaver (BITACORA_SEQ.NEXTVAL)
                String sql = "INSERT INTO BITACORA (ID_BITACORA, OPERACION, DATOS_RECIBIDOS, RESULTADO) VALUES (BITACORA_SEQ.NEXTVAL, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, operacion); 
                    ps.setString(2, datos); 
                    ps.setString(3, resultado);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                System.out.println("Error en bitácora: " + e.getMessage());
            }
        }
    }

    // 3. CONTROLADOR
    static class VehiculoController implements HttpHandler {
        private VehiculoService service = new VehiculoService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String respuestaTexto = "";
                int codigoHTTP = 200;

                try {
                    JSONObject json = new JSONObject(body);
                    Vehiculo v = new Vehiculo();
                    v.id = json.getInt("id"); 
                    v.marca = json.getString("marca");
                    v.modelo = json.getString("modelo"); 
                    v.anio = json.getInt("anio");
                    v.precio = json.getDouble("precio"); 
                    v.color = json.getString("color");

                    String resultado = service.registrarVehiculo(v, body);
                    if (resultado.equals("Éxito")) {
                        respuestaTexto = "{\"mensaje\": \"Vehículo registrado en DB vía API y Bitácora actualizada.\"}";
                    } else {
                        respuestaTexto = "{\"error\": \"Error al guardar en BD: " + resultado + "\"}";
                        codigoHTTP = 500;
                    }
                } catch (Exception e) {
                    respuestaTexto = "{\"error\": \"Formato JSON inválido.\"}";
                    codigoHTTP = 400;
                }

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(codigoHTTP, respuestaTexto.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(respuestaTexto.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }
    }
}