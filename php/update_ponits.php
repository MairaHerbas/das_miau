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

$username = $_POST['username'];
$puntos = (int)$_POST['puntos']; // Puede ser 10 o -10

$sql_user = "UPDATE usuarios SET puntos_usuario = puntos_usuario + $puntos WHERE username = '$username'";
$sql_fac = "UPDATE facultades f JOIN usuarios u ON f.id = u.facultad_id 
            SET f.puntos_totales = f.puntos_totales + $puntos WHERE u.username = '$username'";

mysqli_query($con, $sql_user);
mysqli_query($con, $sql_fac);
echo json_encode(array("status" => "ok", "mensaje" => "Puntos actualizados"));
?>
