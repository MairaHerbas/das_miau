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


$sql = "SELECT id, nombre FROM facultades ORDER BY nombre ASC";
$resultado = mysqli_query($con, $sql);

$facultades = array();
while($row = mysqli_fetch_assoc($resultado)) {
    $facultades[] = array(
        "id" => (int)$row['id'],
        "nombre" => $row['nombre']
    );
}

echo json_encode($facultades);
mysqli_close($con);
?>
