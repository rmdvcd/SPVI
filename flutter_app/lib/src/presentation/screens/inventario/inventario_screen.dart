import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/spvi_colors.dart';
import '../../../core/utils/haptics.dart';
import '../../../domain/models/models.dart';
import '../../providers/spvi_providers.dart';
import '../../widgets/spvi_widgets.dart';

class InventarioScreen extends ConsumerStatefulWidget {
  const InventarioScreen({super.key});

  @override
  ConsumerState<InventarioScreen> createState() => _InventarioScreenState();
}

class _InventarioScreenState extends ConsumerState<InventarioScreen> {
  int _categoriaFiltro = 0; // 0: Todos, 1: Productos, 2: Elaborados, 3: Insumos
  String _busqueda = "";

  void _abrirFormularioProducto([ProductoModel? producto]) {
    final nombreCtrl = TextEditingController(text: producto?.nombre ?? "");
    final categoriaCtrl = TextEditingController(text: producto?.categoria ?? "General");
    final precioCtrl = TextEditingController(text: producto != null ? producto.precioVenta.toDouble.toStringAsFixed(0) : "");
    final stockCtrl = TextEditingController(text: producto != null ? producto.stock.toInt().toString() : "10");
    TipoInventario tipo = producto?.tipo ?? TipoInventario.producto;

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) => Container(
          height: MediaQuery.of(context).size.height * 0.85,
          color: CupertinoColors.systemBackground.resolveFrom(context),
          child: SafeArea(
            top: false,
            child: Column(
              children: [
                CupertinoNavigationBar(
                  middle: Text(producto == null ? "Nuevo Producto" : "Editar Producto"),
                  leading: CupertinoButton(
                    padding: EdgeInsets.zero,
                    child: const Text("Cancelar"),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                  trailing: CupertinoButton(
                    padding: EdgeInsets.zero,
                    child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: () {
                      final nom = nombreCtrl.text.trim();
                      final pDouble = double.tryParse(precioCtrl.text) ?? 0.0;
                      final stDouble = double.tryParse(stockCtrl.text) ?? 0.0;
                      if (nom.isEmpty || pDouble <= 0) return;

                      final nuevo = ProductoModel(
                        id: producto?.id ?? "prod-${DateTime.now().millisecondsSinceEpoch}",
                        nombre: nom,
                        categoria: categoriaCtrl.text.trim().isEmpty ? "General" : categoriaCtrl.text.trim(),
                        precioVenta: Money.fromDouble(pDouble),
                        stock: stDouble,
                        tipo: tipo,
                      );

                      ref.read(inventarioProvider.notifier).agregarOEditarProducto(nuevo);
                      SpviHaptics.mediumImpact();
                      Navigator.pop(ctx);
                    },
                  ),
                ),
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(16.0),
                    children: [
                      const Text("Tipo de Ítem", style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8.0),
                      SizedBox(
                        width: double.infinity,
                        child: CupertinoSlidingSegmentedControl<TipoInventario>(
                          groupValue: tipo,
                          children: const {
                            TipoInventario.producto: Text("Producto"),
                            TipoInventario.elaborado: Text("Elaborado"),
                          },
                          onValueChanged: (val) {
                            if (val != null) {
                              setModalState(() => tipo = val);
                            }
                          },
                        ),
                      ),
                      const SizedBox(height: 16.0),
                      const Text("Nombre del Producto", style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 6.0),
                      CupertinoTextField(
                        controller: nombreCtrl,
                        placeholder: "Ej. Malta Bucanero",
                      ),
                      const SizedBox(height: 16.0),
                      const Text("Categoría", style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 6.0),
                      CupertinoTextField(
                        controller: categoriaCtrl,
                        placeholder: "Ej. Bebidas, Alimentos",
                      ),
                      const SizedBox(height: 16.0),
                      Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text("Precio Venta (CUP)", style: TextStyle(fontWeight: FontWeight.w600)),
                                const SizedBox(height: 6.0),
                                CupertinoTextField(
                                  controller: precioCtrl,
                                  placeholder: "0.00",
                                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 12.0),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text("Stock Inicial", style: TextStyle(fontWeight: FontWeight.w600)),
                                const SizedBox(height: 6.0),
                                CupertinoTextField(
                                  controller: stockCtrl,
                                  placeholder: "0",
                                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _abrirFormularioInsumo([InsumoModel? insumo]) {
    final nombreCtrl = TextEditingController(text: insumo?.nombre ?? "");
    final unidadCtrl = TextEditingController(text: insumo?.unidadMedida ?? "u");
    final cantidadCtrl = TextEditingController(text: insumo != null ? insumo.cantidad.toString() : "5");

    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        height: MediaQuery.of(context).size.height * 0.75,
        color: CupertinoColors.systemBackground.resolveFrom(context),
        child: SafeArea(
          top: false,
          child: Column(
            children: [
              CupertinoNavigationBar(
                middle: Text(insumo == null ? "Nuevo Insumo" : "Editar Insumo"),
                leading: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Cancelar"),
                  onPressed: () => Navigator.pop(ctx),
                ),
                trailing: CupertinoButton(
                  padding: EdgeInsets.zero,
                  child: const Text("Guardar", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: () {
                    final nom = nombreCtrl.text.trim();
                    final cant = double.tryParse(cantidadCtrl.text) ?? 0.0;
                    if (nom.isEmpty) return;

                    final nuevo = InsumoModel(
                      id: insumo?.id ?? "ins-${DateTime.now().millisecondsSinceEpoch}",
                      nombre: nom,
                      unidadMedida: unidadCtrl.text.trim().isEmpty ? "u" : unidadCtrl.text.trim(),
                      cantidad: cant,
                    );

                    ref.read(insumosProvider.notifier).agregarOEditar(nuevo);
                    SpviHaptics.mediumImpact();
                    Navigator.pop(ctx);
                  },
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    const Text("Nombre del Insumo", style: TextStyle(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6.0),
                    CupertinoTextField(
                      controller: nombreCtrl,
                      placeholder: "Ej. Azúcar Blanca, Café grano",
                    ),
                    const SizedBox(height: 16.0),
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text("Unidad (u, kg, lb)", style: TextStyle(fontWeight: FontWeight.w600)),
                              const SizedBox(height: 6.0),
                              CupertinoTextField(
                                controller: unidadCtrl,
                                placeholder: "kg",
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12.0),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text("Cantidad", style: TextStyle(fontWeight: FontWeight.w600)),
                              const SizedBox(height: 6.0),
                              CupertinoTextField(
                                controller: cantidadCtrl,
                                placeholder: "0.0",
                                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final productos = ref.watch(inventarioProvider);
    final insumos = ref.watch(insumosProvider);

    final productosFiltrados = productos.where((p) {
      final matchesSearch = p.nombre.toLowerCase().contains(_busqueda.toLowerCase()) ||
          p.categoria.toLowerCase().contains(_busqueda.toLowerCase());
      if (!matchesSearch) return false;
      if (_categoriaFiltro == 1) return p.tipo == TipoInventario.producto;
      if (_categoriaFiltro == 2) return p.tipo == TipoInventario.elaborado;
      if (_categoriaFiltro == 3) return false;
      return true;
    }).toList();

    final insumosFiltrados = insumos.where((i) {
      if (_categoriaFiltro == 1 || _categoriaFiltro == 2) return false;
      return i.nombre.toLowerCase().contains(_busqueda.toLowerCase());
    }).toList();

    return CupertinoPageScaffold(
      navigationBar: CupertinoNavigationBar(
        largeTitle: const Text("Inventario"),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          child: const Icon(CupertinoIcons.add_circled_solid, size: 28),
          onPressed: () {
            SpviHaptics.lightImpact();
            showCupertinoModalPopup(
              context: context,
              builder: (ctx) => CupertinoActionSheet(
                title: const Text("Agregar a Inventario"),
                actions: [
                  CupertinoActionSheetAction(
                    onPressed: () {
                      Navigator.pop(ctx);
                      _abrirFormularioProducto();
                    },
                    child: const Text("Nuevo Producto o Elaborado"),
                  ),
                  CupertinoActionSheetAction(
                    onPressed: () {
                      Navigator.pop(ctx);
                      _abrirFormularioInsumo();
                    },
                    child: const Text("Nuevo Insumo"),
                  ),
                ],
                cancelButton: CupertinoActionSheetAction(
                  isDestructiveAction: true,
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text("Cancelar"),
                ),
              ),
            );
          },
        ),
      ),
      child: SafeArea(
        child: Column(
          children: [
            // Barra de búsqueda iOS nativa
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: CupertinoSearchTextField(
                placeholder: "Buscar productos o insumos...",
                onChanged: (val) => setState(() => _busqueda = val),
              ),
            ),

            // Segmented Control de Filtro
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
              child: SizedBox(
                width: double.infinity,
                child: CupertinoSlidingSegmentedControl<int>(
                  groupValue: _categoriaFiltro,
                  children: const {
                    0: Text("Todos"),
                    1: Text("Productos"),
                    2: Text("Elaborados"),
                    3: Text("Insumos"),
                  },
                  onValueChanged: (val) {
                    if (val != null) {
                      SpviHaptics.selectionClick();
                      setState(() => _categoriaFiltro = val);
                    }
                  },
                ),
              ),
            ),

            const SizedBox(height: 8.0),

            // Lista agrupada
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16.0),
                children: [
                  if (_categoriaFiltro != 3 && productosFiltrados.isNotEmpty) ...[
                    const Padding(
                      padding: EdgeInsets.only(left: 4.0, bottom: 6.0),
                      child: Text(
                        "PRODUCTOS Y ELABORADOS",
                        style: TextStyle(
                          fontSize: 13.0,
                          fontWeight: FontWeight.bold,
                          color: CupertinoColors.secondaryLabel,
                        ),
                      ),
                    ),
                    ...productosFiltrados.map((p) => Padding(
                          padding: const EdgeInsets.only(bottom: 8.0),
                          child: SpviCard(
                            padding: const EdgeInsets.all(12.0),
                            onTap: () => _abrirFormularioProducto(p),
                            child: Row(
                              children: [
                                Container(
                                  width: 44,
                                  height: 44,
                                  decoration: BoxDecoration(
                                    color: p.tipo == TipoInventario.elaborado
                                        ? SpviColors.orange.withOpacity(0.15)
                                        : SpviColors.navy.withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(8.0),
                                  ),
                                  child: Icon(
                                    p.tipo == TipoInventario.elaborado
                                        ? CupertinoIcons.wrench_fill
                                        : CupertinoIcons.cube_box_fill,
                                    color: p.tipo == TipoInventario.elaborado
                                        ? SpviColors.orange
                                        : SpviColors.navy,
                                  ),
                                ),
                                const SizedBox(width: 12.0),
                                Expanded(
                                  // Petición 2: debajo del nombre, cuánto se puede hacer y su unidad; la
                                  // categoría va debajo de eso. Los precios están a la derecha.
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        p.nombre,
                                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
                                      ),
                                      const SizedBox(height: 2.0),
                                      Text(
                                        "Puedes hacer: ${p.stock.toInt()} u",
                                        style: TextStyle(
                                          fontSize: 13.0,
                                          color: p.stockCritico ? SpviColors.error : CupertinoColors.secondaryLabel,
                                          fontWeight: p.stockCritico ? FontWeight.bold : FontWeight.normal,
                                        ),
                                      ),
                                      const SizedBox(height: 2.0),
                                      Text(
                                        p.categoria,
                                        style: TextStyle(
                                          fontSize: 12.0,
                                          color: CupertinoColors.secondaryLabel.resolveFrom(context),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 8.0),
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    if (p.costo != null)
                                      Text(
                                        p.costo!.formatted,
                                        style: TextStyle(
                                          fontSize: 14.0,
                                          fontWeight: FontWeight.w600,
                                          color: CupertinoColors.secondaryLabel.resolveFrom(context),
                                        ),
                                      ),
                                    const SizedBox(height: 2.0),
                                    Text(
                                      p.precioVenta.formatted,
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0, color: SpviColors.navy),
                                    ),
                                  ],
                                ),
                                const SizedBox(width: 6.0),
                                const Icon(CupertinoIcons.chevron_right, size: 16.0, color: CupertinoColors.systemGrey3),
                              ],
                            ),
                          ),
                        )),
                    const SizedBox(height: 16.0),
                  ],

                  if ((_categoriaFiltro == 0 || _categoriaFiltro == 3) && insumosFiltrados.isNotEmpty) ...[
                    const Padding(
                      padding: EdgeInsets.only(left: 4.0, bottom: 6.0),
                      child: Text(
                        "INSUMOS",
                        style: TextStyle(
                          fontSize: 13.0,
                          fontWeight: FontWeight.bold,
                          color: CupertinoColors.secondaryLabel,
                        ),
                      ),
                    ),
                    ...insumosFiltrados.map((i) => Padding(
                          padding: const EdgeInsets.only(bottom: 8.0),
                          child: SpviCard(
                            padding: const EdgeInsets.all(12.0),
                            onTap: () => _abrirFormularioInsumo(i),
                            child: Row(
                              children: [
                                Container(
                                  width: 44,
                                  height: 44,
                                  decoration: BoxDecoration(
                                    color: SpviColors.amber.withOpacity(0.2),
                                    borderRadius: BorderRadius.circular(8.0),
                                  ),
                                  child: const Icon(CupertinoIcons.bag_fill, color: SpviColors.amber),
                                ),
                                const SizedBox(width: 12.0),
                                Expanded(
                                  // Petición 2, igual que en los productos: cantidad y unidad bajo el
                                  // nombre, categoría debajo y precio a la derecha.
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        i.nombre,
                                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
                                      ),
                                      const SizedBox(height: 2.0),
                                      Text(
                                        "Disponible: ${i.cantidad} ${i.unidadMedida}",
                                        style: TextStyle(
                                          fontSize: 13.0,
                                          color: i.stockCritico ? SpviColors.orange : CupertinoColors.secondaryLabel,
                                          fontWeight: i.stockCritico ? FontWeight.bold : FontWeight.normal,
                                        ),
                                      ),
                                      const SizedBox(height: 2.0),
                                      Text(
                                        "Insumo",
                                        style: TextStyle(
                                          fontSize: 12.0,
                                          color: CupertinoColors.secondaryLabel.resolveFrom(context),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 8.0),
                                if (i.precioCosto != null)
                                  Text(
                                    i.precioCosto!.formatted,
                                    style: TextStyle(
                                      fontSize: 14.0,
                                      fontWeight: FontWeight.w600,
                                      color: CupertinoColors.secondaryLabel.resolveFrom(context),
                                    ),
                                  ),
                                const SizedBox(width: 6.0),
                                const Icon(CupertinoIcons.chevron_right, size: 16.0, color: CupertinoColors.systemGrey3),
                              ],
                            ),
                          ),
                        )),
                  ],

                  if (productosFiltrados.isEmpty && insumosFiltrados.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 40.0),
                      child: Center(
                        child: Text(
                          "No se encontraron elementos en inventario",
                          style: TextStyle(color: CupertinoColors.secondaryLabel),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
