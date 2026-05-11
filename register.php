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

$username = $_POST['username'] ?? '';
$password = $_POST['password'] ?? '';
$nombre = $_POST['nombre'] ?? '';
$email = $_POST['email'] ?? '';
$facultad_id = $_POST['facultad_id'] ?? ''; // Ahora recibimos el ID directamente
$foto_base64 = $_POST['foto'] ?? '';

if (empty($username) || empty($password) || empty($facultad_id)) {
    echo json_encode(array("status" => "error", "mensaje" => "Faltan datos obligatorios"));
    exit();
}

//comprobar si ya existe
$sql_comprobar = "SELECT * FROM usuarios WHERE username = '$username'";
$resultado_comprobar = mysqli_query($con, $sql_comprobar);
if (mysqli_num_rows($resultado_comprobar) > 0) {
    echo json_encode(array("status" => "error", "mensaje" => "El usuario ya existe"));
    exit();
}

$password_hashed = password_hash($password, PASSWORD_DEFAULT);

//foto
$foto_name = "";
if (!empty($foto_base64)) {
    $directorio = "uploads/";
    if (!is_dir($directorio)) { mkdir($directorio, 0777, true); }
    $foto_name = $directorio . $username . ".jpg";
    file_put_contents($foto_name, base64_decode($foto_base64));
}

//insertar usando el facultad_id
$sql_insertar = "INSERT INTO usuarios (username, password, nombre, email, facultad_id, foto_path) 
                 VALUES ('$username', '$password_hashed', '$nombre', '$email', '$facultad_id', '$foto_name')";

if (mysqli_query($con, $sql_insertar)) {
    echo json_encode(array(
        "status" => "ok",
        "mensaje" => "Usuario registrado correctamente",
        "id_usuario" => mysqli_insert_id($con)
    ));
} else {
    echo json_encode(array("status" => "error", "mensaje" => mysqli_error($con)));
}
?>
