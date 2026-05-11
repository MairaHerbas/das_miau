<?php
$DB_SERVER = "db";
$DB_USER = "admin";
$DB_PASS = "test";
$DB_DATABASE = "database";

$con = mysqli_connect($DB_SERVER, $DB_USER, $DB_PASS, $DB_DATABASE);
if (mysqli_connect_errno()) {
    echo json_encode(array("status" => "error", "mensaje" => "error de conexion"));
    exit();
}

$usuario = $_POST['usuario'] ?? '';
$contrasena = $_POST['contrasena'] ?? '';

if (empty($usuario) || empty($contrasena)) {
    echo json_encode(array("status" => "error", "mensaje" => "Faltan datos de inicio de sesión"));
    exit();
}

$sql = "SELECT id, password, nombre, email, facultad_id, puntos_usuario FROM usuarios WHERE username = '$usuario'";
$resultado = mysqli_query($con, $sql);

if ($resultado && mysqli_num_rows($resultado) > 0) {
    $row = mysqli_fetch_assoc($resultado);
    $hash_guardado = $row['password'];

    if (password_verify($contrasena, $hash_guardado)) {
        echo json_encode(array(
            "status" => "ok",
            "mensaje" => "Inicio de sesión correcto",
            "id_usuario" => $row['id'],
            "nombre_completo" => $row['nombre'],
            "email" => $row['email'],
            "facultad_id" => $row['facultad_id'],
	    "puntos_usuario"=>(int)$row["puntos_usuario"]
        ));
    } else {
        echo json_encode(array("status" => "error", "mensaje" => "Contraseña incorrecta"));
    }
} else {
    echo json_encode(array("status" => "error", "mensaje" => "El usuario no existe"));
}
mysqli_close($con);
?>
