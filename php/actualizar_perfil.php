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


$id_usuario = $_POST['id_usuario'] ?? '';
$nombre = $_POST['nombre'] ?? '';
$email = $_POST['email'] ?? '';
$username = $_POST['username'] ?? '';
$password = $_POST['contrasena'] ?? '';
$facultad_id = $_POST['facultad_id'] ?? '';
$foto_base64 = $_POST['imagen'] ?? '';

if(empty($id_usuario)) {
    die("Error: Falta el ID de usuario");
}

$sql = "UPDATE usuarios SET nombre='$nombre', email='$email', username='$username', facultad_id='$facultad_id'";

if (!empty($password)) {
    $hash = password_hash($password, PASSWORD_DEFAULT);
    $sql .= ", password='$hash'";
}

if (!empty($foto_base64)) {
    $directorio = "uploads/";
    if (!is_dir($directorio)) { mkdir($directorio, 0777, true); }

    //guardamos la foto con el username (sobreescribir la antigua)
    $foto_path = $directorio . $username . ".jpg";
    if (file_put_contents($foto_path, base64_decode($foto_base64))) {
        $sql .= ", foto_path='$foto_path'";
    }
}

$sql .= " WHERE id='$id_usuario'";

if (mysqli_query($con, $sql)) {
    echo "success";
} else {
    echo "error: " . mysqli_error($con);
}
?>
