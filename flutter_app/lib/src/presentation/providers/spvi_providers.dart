import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../domain/models/models.dart';

final uuidProvider = Provider((ref) => const Uuid());

/// Licencia Provider
final licenciaProvider = StateNotifierProvider<LicenciaNotifier, LicenciaInfo>((ref) {
  return LicenciaNotifier();
});

class LicenciaNotifier extends StateNotifier<LicenciaInfo> {
  LicenciaNotifier() : super(const LicenciaInfo(tipo: TipoLicencia.prueba, diasRestantesPrueba: 7));

  void activarConClave(String clave) {
    if (clave.trim().length >= 10) {
      state = LicenciaInfo(
        tipo: TipoLicencia.activa,
        clave: clave.trim(),
        titular: "Usuario Licenciado",
      );
    }
  }
}

/// Perfil y Ajustes Provider
final perfilProvider = StateNotifierProvider<PerfilNotifier, PerfilNegocioModel>((ref) {
  return PerfilNotifier();
});

class PerfilNotifier extends StateNotifier<PerfilNegocioModel> {
  PerfilNotifier() : super(const PerfilNegocioModel());

  void actualizarPerfil({
    String? nombre,
    String? apellidos,
    String? carnet,
    String? nombreNegocio,
    String? telefono,
    String? tarjeta,
  }) {
    state = state.copyWith(
      nombre: nombre,
      apellidos: apellidos,
      carnet: carnet,
      nombreNegocio: nombreNegocio,
      telefonoTransferencia: telefono,
      tarjetaTransferencia: tarjeta,
    );
  }

  void configurarSeguridad({required bool habilitar, String? pin}) {
    state = state.copyWith(
      accesoClaveHabilitado: habilitar,
      clavePin: pin,
    );
  }
}

/// Turno Provider
final turnoProvider = StateNotifierProvider<TurnoNotifier, TurnoModel?>((ref) {
  return TurnoNotifier();
});

class TurnoNotifier extends StateNotifier<TurnoModel?> {
  TurnoNotifier() : super(null);

  void abrirTurno(Money fondo) {
    state = TurnoModel(
      id: "turno-${DateTime.now().millisecondsSinceEpoch}",
      fechaApertura: DateTime.now(),
      fondoInicial: fondo,
      efectivoEsperado: fondo,
      estado: EstadoTurno.abierto,
    );
  }

  void registrarVenta(Money monto, MetodoPago metodo) {
    if (state == null) return;
    if (metodo == MetodoPago.efectivo) {
      state = state!.copyWith(
        efectivoEsperado: state!.efectivoEsperado + monto,
        totalVentasEfectivo: state!.totalVentasEfectivo + monto,
        totalOperaciones: state!.totalOperaciones + 1,
      );
    } else {
      state = state!.copyWith(
        totalVentasTransferencia: state!.totalVentasTransferencia + monto,
        totalOperaciones: state!.totalOperaciones + 1,
      );
    }
  }

  void registrarMovimiento(TipoMovimientoCaja tipo, Money monto) {
    if (state == null) return;
    if (tipo == TipoMovimientoCaja.entrada) {
      state = state!.copyWith(efectivoEsperado: state!.efectivoEsperado + monto);
    } else {
      state = state!.copyWith(efectivoEsperado: state!.efectivoEsperado - monto);
    }
  }

  void cerrarTurno({required Money efectivoReal, String? nota}) {
    if (state == null) return;
    state = state!.copyWith(
      fechaCierre: DateTime.now(),
      efectivoReal: efectivoReal,
      estado: EstadoTurno.cerrado,
      notaCierre: nota,
    );
  }
}

/// Inventario Provider (Productos)
final inventarioProvider = StateNotifierProvider<InventarioNotifier, List<ProductoModel>>((ref) {
  return InventarioNotifier();
});

class InventarioNotifier extends StateNotifier<List<ProductoModel>> {
  InventarioNotifier() : super([
    const ProductoModel(
      id: "prod-1",
      nombre: "Café Expreso Doble",
      categoria: "Cafetería",
      precioVenta: Money(35000), // 350 CUP
      costo: Money(15000),
      stock: 45.0,
      stockMinimo: 10.0,
      tipo: TipoInventario.producto,
    ),
    const ProductoModel(
      id: "prod-2",
      nombre: "Sándwich de Jamón y Queso",
      categoria: "Alimentos",
      precioVenta: Money(65000), // 650 CUP
      costo: Money(30000),
      stock: 3.0, // Alerta stock bajo
      stockMinimo: 5.0,
      tipo: TipoInventario.elaborado,
    ),
    const ProductoModel(
      id: "prod-3",
      nombre: "Refresco en Lata 330ml",
      categoria: "Bebidas",
      precioVenta: Money(22000),
      costo: Money(14000),
      stock: 18.0,
      stockMinimo: 5.0,
      tipo: TipoInventario.producto,
    ),
  ]);

  void agregarOEditarProducto(ProductoModel producto) {
    final idx = state.indexWhere((p) => p.id == producto.id);
    if (idx >= 0) {
      state = [
        for (int i = 0; i < state.length; i++)
          if (i == idx) producto else state[i]
      ];
    } else {
      state = [...state, producto];
    }
  }

  void eliminarProducto(String id) {
    state = state.where((p) => p.id != id).toList();
  }

  void descontarStock(String id, double cantidad) {
    state = [
      for (final p in state)
        if (p.id == id)
          ProductoModel(
            id: p.id,
            nombre: p.nombre,
            categoria: p.categoria,
            precioVenta: p.precioVenta,
            costo: p.costo,
            stock: (p.stock - cantidad).clamp(0.0, 999999.0),
            stockMinimo: p.stockMinimo,
            codigoBarras: p.codigoBarras,
            tipo: p.tipo,
            fotoUri: p.fotoUri,
            insumosIds: p.insumosIds,
          )
        else
          p
    ];
  }
}

/// Insumos Provider
final insumosProvider = StateNotifierProvider<InsumosNotifier, List<InsumoModel>>((ref) {
  return InsumosNotifier();
});

class InsumosNotifier extends StateNotifier<List<InsumoModel>> {
  InsumosNotifier() : super([
    const InsumoModel(
      id: "ins-1",
      nombre: "Café en Grano Torrefacto",
      unidadMedida: "kg",
      cantidad: 1.5, // Stock crítico
      minimo: 3.0,
      precioCosto: Money(120000),
    ),
    const InsumoModel(
      id: "ins-2",
      nombre: "Pan Flauta Artesanal",
      unidadMedida: "u",
      cantidad: 24.0,
      minimo: 10.0,
      precioCosto: Money(6000),
    ),
  ]);

  void agregarOEditar(InsumoModel insumo) {
    final idx = state.indexWhere((i) => i.id == insumo.id);
    if (idx >= 0) {
      state = [
        for (int i = 0; i < state.length; i++)
          if (i == idx) insumo else state[i]
      ];
    } else {
      state = [...state, insumo];
    }
  }

  void eliminar(String id) {
    state = state.where((i) => i.id != id).toList();
  }
}

/// Servicios Provider
final serviciosProvider = StateNotifierProvider<ServiciosNotifier, List<ServicioModel>>((ref) {
  return ServiciosNotifier();
});

class ServiciosNotifier extends StateNotifier<List<ServicioModel>> {
  ServiciosNotifier() : super([
    const ServicioModel(
      id: "srv-1",
      nombre: "Mantenimiento Express",
      tipo: "Técnico",
      importe: Money(150000), // 1500 CUP
      descripcion: "Limpieza y diagnóstico rápido",
    ),
    const ServicioModel(
      id: "srv-2",
      nombre: "Copia de Seguridad y Migración",
      tipo: "Digital",
      importe: Money(80000),
      descripcion: "Exportación de respaldo seguro",
    ),
  ]);

  void agregarOEditar(ServicioModel servicio) {
    final idx = state.indexWhere((s) => s.id == servicio.id);
    if (idx >= 0) {
      state = [
        for (int i = 0; i < state.length; i++)
          if (i == idx) servicio else state[i]
      ];
    } else {
      state = [...state, servicio];
    }
  }

  void eliminar(String id) {
    state = state.where((s) => s.id != id).toList();
  }
}

/// Clientes Fijos Provider
final clientesProvider = StateNotifierProvider<ClientesNotifier, List<ClienteFijoModel>>((ref) {
  return ClientesNotifier();
});

class ClientesNotifier extends StateNotifier<List<ClienteFijoModel>> {
  ClientesNotifier() : super([
    const ClienteFijoModel(
      carnet: "88051212345",
      nombre: "Carlos Martínez",
      telefono: "53123456",
      direccion: "Calle 23 #456",
    ),
    const ClienteFijoModel(
      carnet: "94032254321",
      nombre: "Ana Rodríguez",
      telefono: "58765432",
      direccion: "Ave 5ta e/ 10 y 12",
    ),
  ]);

  void agregarOActualizar(ClienteFijoModel cliente) {
    final idx = state.indexWhere((c) => c.carnet == cliente.carnet);
    if (idx >= 0) {
      state = [
        for (int i = 0; i < state.length; i++)
          if (i == idx) cliente else state[i]
      ];
    } else {
      state = [...state, cliente];
    }
  }
}

/// Ventas Realizadas Provider
final ventasHistorialProvider = StateNotifierProvider<VentasNotifier, List<VentaModel>>((ref) {
  return VentasNotifier();
});

class VentasNotifier extends StateNotifier<List<VentaModel>> {
  VentasNotifier() : super([]);

  void registrarVenta(VentaModel venta) {
    state = [venta, ...state];
  }

  void anularVenta(String id) {
    state = [
      for (final v in state)
        if (v.id == id)
          VentaModel(
            id: v.id,
            turnoId: v.turnoId,
            fecha: v.fecha,
            lineas: v.lineas,
            total: v.total,
            metodoPago: v.metodoPago,
            numeroTransaccionTransferencia: v.numeroTransaccionTransferencia,
            cliente: v.cliente,
            anulada: true,
          )
        else
          v
    ];
  }
}

/// Movimientos de Caja Provider
final movimientosCajaProvider = StateNotifierProvider<MovimientosNotifier, List<MovimientoCajaModel>>((ref) {
  return MovimientosNotifier();
});

class MovimientosNotifier extends StateNotifier<List<MovimientoCajaModel>> {
  MovimientosNotifier() : super([]);

  void registrarMovimiento(MovimientoCajaModel mov) {
    state = [mov, ...state];
  }
}
