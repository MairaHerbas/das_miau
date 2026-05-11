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

$sql = "SELECT nombre,
               puntos_totales,
               (puntos_totales / NULLIF((SELECT COUNT(*) FROM usuarios), 0)) as media_puntos
        FROM facultades
        ORDER BY media_puntos DESC
        LIMIT 3";

$result = mysqli_query($con, $sql);

$ranking = array();
while($row = mysqli_fetch_assoc($result)) {
    //redondeamos a 2 decimales
    $row['media_puntos'] = $row['media_puntos'] !== null ? round((float)$row['media_puntos'], 2) : 0;

    $ranking[] = $row;
}

echo json_encode($ranking);
?>
