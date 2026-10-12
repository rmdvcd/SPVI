import 'dart:convert';

/// Moneda en centavos para precisión sin decimales flotantes
class Money {
  final int centavos;
  const Money(this.centavos);
  static const zero = Money(0);

  double get toDouble => centavos / 100.0;
  String get formatted => '${toDouble.toStringAsFixed(2)} CUP';

  Money operator +(Money other) => Money(centavos + other.centavos);
  Money operator -(Money other) => Money(centavos - other.centavos);
  Money operator *(int factor) => Money(centavos * factor);

  factory Money.fromDouble(double value) => Money((value * 100).round());
}

/// Estado de la Licencia Offline
enum TipoLicencia { prueba, activa, vencida }

class LicenciaInfo {
  final TipoLicencia tipo;
  final int diasRestantesPrueba;
  final String? clave;
  final String? titular;

  const LicenciaInfo({
    required this.tipo,
    this.diasRestantesPrueba = 7,
    this.clave,
    this.titular,
  });

  bool get estaOperativo => tipo != TipoLicencia.vencida;
}

/// Estado y Datos del Turno
enum EstadoTurno { abierto, cerrado }

class TurnoModel {
  final String id;
  final DateTime fechaApertura;
  final DateTime? fechaCierre;
  final Money fondoInicial;
  final Money efectivoEsperado;
  final Money efectivoReal;
  final Money totalVentasEfectivo;
  final Money totalVentasTransferencia;
  final int totalOperaciones;
  final EstadoTurno estado;
  final String? notaCierre;

  const TurnoModel({
    required this.id,
    required this.fechaApertura,
    this.fechaCierre,
    required this.fondoInicial,
    this.efectivoEsperado = Money.zero,
    this.efectivoReal = Money.zero,
    this.totalVentasEfectivo = Money.zero,
    this.totalVentasTransferencia = Money.zero,
    this.totalOperaciones = 0,
    required this.estado,
    this.notaCierre,
  });

  TurnoModel copyWith({
    String? id,
    DateTime? fechaApertura,
    DateTime? fechaCierre,
    Money? fondoInicial,
    Money? efectivoEsperado,
    Money? efectivoReal,
    Money? totalVentasEfectivo,
    Money? totalVentasTransferencia,
    int? totalOperaciones,
    EstadoTurno? estado,
    String? notaCierre,
  }) {
    return TurnoModel(
      id: id ?? this.id,
      fechaApertura: fechaApertura ?? this.fechaApertura,
      fechaCierre: fechaCierre ?? this.fechaCierre,
      fondoInicial: fondoInicial ?? this.fondoInicial,
      efectivoEsperado: efectivoEsperado ?? this.efectivoEsperado,
      efectivoReal: efectivoReal ?? this.efectivoReal,
      totalVentasEfectivo: totalVentasEfectivo ?? this.totalVentasEfectivo,
      totalVentasTransferencia: totalVentasTransferencia ?? this.totalVentasTransferencia,
      totalOperaciones: totalOperaciones ?? this.totalOperaciones,
      estado: estado ?? this.estado,
      notaCierre: notaCierre ?? this.notaCierre,
    );
  }
}

/// Tipos de ítems en el Inventario
enum TipoInventario { producto, elaborado, insumo }

class ProductoModel {
  final String id;
  final String nombre;
  final String categoria;
  final Money precioVenta;
  final Money? costo;
  final double stock;
  final double stockMinimo;
  final String? codigoBarras;
  final TipoInventario tipo;
  final String? fotoUri;
  final List<String> insumosIds; // Para elaborados

  const ProductoModel({
    required this.id,
    required this.nombre,
    required this.categoria,
    required this.precioVenta,
    this.costo,
    required this.stock,
    this.stockMinimo = 5.0,
    this.codigoBarras,
    this.tipo = TipoInventario.producto,
    this.fotoUri,
    this.insumosIds = const [],
  });

  bool get stockCritico => stock <= stockMinimo;
}

/// Modelo de Insumo
class InsumoModel {
  final String id;
  final String nombre;
  final String unidadMedida; // "u", "kg", "lb", "L", "ml"
  final double cantidad;
  final double minimo;
  final Money? precioCosto;

  const InsumoModel({
    required this.id,
    required this.nombre,
    required this.unidadMedida,
    required this.cantidad,
    this.minimo = 2.0,
    this.precioCosto,
  });

  bool get stockCritico => cantidad <= minimo;
}

/// Modelo de Servicio
class ServicioModel {
  final String id;
  final String nombre;
  final String tipo; // Categoría o rubro libre
  final Money importe;
  final String? descripcion;
  final String? fotoUri;

  const ServicioModel({
    required this.id,
    required this.nombre,
    required this.tipo,
    required this.importe,
    this.descripcion,
    this.fotoUri,
  });
}

/// Clientes Fijos (0.27.0)
class ClienteFijoModel {
  final String carnet;
  final String nombre;
  final String? telefono;
  final String? direccion;

  const ClienteFijoModel({
    required this.carnet,
    required this.nombre,
    this.telefono,
    this.direccion,
  });
}

/// Ventas y Transacciones
enum MetodoPago { efectivo, transferencia }
enum TipoVenta { venta, servicio }

class LineaVentaModel {
  final String itemId;
  final String nombre;
  final double cantidad;
  final Money precioUnitario;
  final TipoVenta tipo;

  const LineaVentaModel({
    required this.itemId,
    required this.nombre,
    required this.cantidad,
    required this.precioUnitario,
    required this.tipo,
  });

  Money get subtotal => Money((precioUnitario.centavos * cantidad).round());
}

class VentaModel {
  final String id;
  final String turnoId;
  final DateTime fecha;
  final List<LineaVentaModel> lineas;
  final Money total;
  final MetodoPago metodoPago;
  final String? numeroTransaccionTransferencia;
  final ClienteFijoModel? cliente;
  final bool anulada;

  const VentaModel({
    required this.id,
    required this.turnoId,
    required this.fecha,
    required this.lineas,
    required this.total,
    required this.metodoPago,
    this.numeroTransaccionTransferencia,
    this.cliente,
    this.anulada = false,
  });
}

/// Movimiento de caja (Entrada / Salida de efectivo)
enum TipoMovimientoCaja { entrada, salida }

class MovimientoCajaModel {
  final String id;
  final String turnoId;
  final DateTime fecha;
  final TipoMovimientoCaja tipo;
  final Money monto;
  final String motivo;

  const MovimientoCajaModel({
    required this.id,
    required this.turnoId,
    required this.fecha,
    required this.tipo,
    required this.monto,
    required this.motivo,
  });
}

/// Perfil del Negocio y Ajustes
enum RolDispositivo { principal, secundaria }

class PerfilNegocioModel {
  final String nombre;
  final String apellidos;
  final String carnet;
  final String nombreNegocio;
  final RolDispositivo rol;
  final bool accesoClaveHabilitado;
  final String? clavePin;
  final String? telefonoTransferencia;
  final String? tarjetaTransferencia;

  const PerfilNegocioModel({
    this.nombre = "Titular de Negocio",
    this.apellidos = "Gómez Pérez",
    this.carnet = "90010112345",
    this.nombreNegocio = "Mi Emprendimiento",
    this.rol = RolDispositivo.principal,
    this.accesoClaveHabilitado = false,
    this.clavePin,
    this.telefonoTransferencia = "52345678",
    this.tarjetaTransferencia = "9202123456789012",
  });

  PerfilNegocioModel copyWith({
    String? nombre,
    String? apellidos,
    String? carnet,
    String? nombreNegocio,
    RolDispositivo? rol,
    bool? accesoClaveHabilitado,
    String? clavePin,
    String? telefonoTransferencia,
    String? tarjetaTransferencia,
  }) {
    return PerfilNegocioModel(
      nombre: nombre ?? this.nombre,
      apellidos: apellidos ?? this.apellidos,
      carnet: carnet ?? this.carnet,
      nombreNegocio: nombreNegocio ?? this.nombreNegocio,
      rol: rol ?? this.rol,
      accesoClaveHabilitado: accesoClaveHabilitado ?? this.accesoClaveHabilitado,
      clavePin: clavePin ?? this.clavePin,
      telefonoTransferencia: telefonoTransferencia ?? this.telefonoTransferencia,
      tarjetaTransferencia: tarjetaTransferencia ?? this.tarjetaTransferencia,
    );
  }
}
