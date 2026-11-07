import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AppEscritorioVehiculos extends JFrame {
    private JTextField txtId, txtMarca, txtModelo, txtAnio, txtPrecio, txtColor;
    
    // Contraseña mala a propósito para forzar el fallo de JDBC y usar la API REST
    private static final String DB_PASSWORD_PRUEBA = "contrasena_mala"; 

    public AppEscritorioVehiculos() {
        setTitle("Registro de Vehículos (Conexión Resiliente)");
        setSize(400, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(7, 2, 10, 10));

        add(new JLabel("ID del Vehículo:")); txtId = new JTextField(); add(txtId);
        add(new JLabel("Marca:")); txtMarca = new JTextField(); add(txtMarca);
        add(new JLabel("Modelo:")); txtModelo = new JTextField(); add(txtModelo);
        add(new JLabel("Año:")); txtAnio = new JTextField(); add(txtAnio);
        add(new JLabel("Precio (Q):")); txtPrecio = new JTextField(); add(txtPrecio);
        add(new JLabel("Color:")); txtColor = new JTextField(); add(txtColor);

        JButton btnGuardar = new JButton("Guardar Vehículo");
        btnGuardar.setBackground(new Color(40, 167, 69));
        btnGuardar.setForeground(Color.WHITE);
        btnGuardar.addActionListener(e -> intentarGuardar());
        
        add(new JLabel("")); add(btnGuardar);
    }

    private void intentarGuardar() {
        if (txtId.getText().isEmpty() || txtMarca.getText().isEmpty() || txtModelo.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor, llene los campos principales.");
            return;
        }

        try {
            int id = Integer.parseInt(txtId.getText());
            int anio = Integer.parseInt(txtAnio.getText());
            double precio = Double.parseDouble(txtPrecio.getText());
            
            // Intento 1: JDBC Directo
            if (guardarDirectoEnBD(id, txtMarca.getText(), txtModelo.getText(), anio, precio, txtColor.getText())) {
                JOptionPane.showMessageDialog(this, "ÉXITO: Registrado directamente en la Base de Datos (JDBC).");
                limpiarCampos();
            } else {
                // Intento 2: Falló BD, enviar por API REST
                enviarPorApi(id, txtMarca.getText(), txtModelo.getText(), anio, precio, txtColor.getText());
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "ID, Año y Precio deben ser números.");
        }
    }

    private boolean guardarDirectoEnBD(int id, String marca, String modelo, int anio, double precio, String color) {
        String url = "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        try (Connection conn = DriverManager.getConnection(url, "SYSTEM", DB_PASSWORD_PRUEBA)) {
            String sql = "INSERT INTO VEHICULOS (ID, MARCA, MODELO, ANIO, PRECIO, COLOR) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id); ps.setString(2, marca); ps.setString(3, modelo);
                ps.setInt(4, anio); ps.setDouble(5, precio); ps.setString(6, color);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            return false; // Retorna false para activar el envío por API
        }
    }

    private void enviarPorApi(int id, String marca, String modelo, int anio, double precio, String color) {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id); json.put("marca", marca); json.put("modelo", modelo);
            json.put("anio", anio); json.put("precio", precio); json.put("color", color);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/api/vehiculos"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JOptionPane.showMessageDialog(this, "ÉXITO: Conexión BD fallida. Guardado vía API REST y registrado en Bitácora.");
                limpiarCampos();
            } else {
                JOptionPane.showMessageDialog(this, "Error en API: " + response.body());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error de red: " + ex.getMessage());
        }
    }

    private void limpiarCampos() {
        txtId.setText(""); txtMarca.setText(""); txtModelo.setText("");
        txtAnio.setText(""); txtPrecio.setText(""); txtColor.setText("");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppEscritorioVehiculos().setVisible(true));
    }
}