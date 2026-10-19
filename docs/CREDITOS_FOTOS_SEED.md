# Créditos de las fotos del seed (solo depuración)

Las **38 fotos** que «Generar datos de prueba» (Ajustes, visible solo con `BuildConfig.DEBUG`) copia al almacén de
fotos del teléfono viven en `app/src/debug/assets/seed/fotos/<clave>.jpg`: **solo van en las compilaciones de
depuración**, no en el APK/AAB de release, y la app no descarga nada de internet.

- Origen: **Wikimedia Commons**, filtrado a licencias que permiten redistribuir (dominio público, CC0, CC BY y
  CC BY-SA). No se usa ninguna imagen con cláusula NC (no comercial) o ND (sin obras derivadas).
- Las copias se recortan/redimensionan a 512 px y se re-codifican a JPEG. Las que son **CC BY-SA** se siguen
  compartiendo bajo la misma licencia (la versión reducida es una obra derivada); las **CC BY** solo piden
  atribución. **CC0** y dominio público no piden nada: se acreditan igual, por trazabilidad.
- Se conservan los nombres de marca que aparezcan en las fotos (productos de aseo, refrescos): son imágenes de
  catálogo para la demo de desarrollo, no una afirmación de que el negocio venda esas marcas.
- Para rehacerlas o sustituirlas: basta con dejar `<clave>.jpg` (cualquier tamaño; la app la reduce sola) en esa
  carpeta. Si falta la imagen de un artículo, el seed lo deja **sin foto** y no falla.
- Las claves son las del catálogo (`CatalogoBodega`): `arroz`, `frijoles-negros`, ... y las de los servicios
  (`corte-pelo`, `manicura`, ...). Los **insumos** y los **Elaborados** no llevan foto (no existe ese campo
  para el insumo y el elaborado no la admite por diseño).

| Artículo (clave) | Imagen original | Autor | Licencia | Origen |
|---|---|---|---|---|
| `aceite-bote` | 2022 sunflower oil - manufacturing dates 01.jpg | Retired electrician | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:2022_sunflower_oil_-_manufacturing_dates_01.jpg) |
| `afilado` | Friedr Dick sharpening knife on grinding wheel.jpeg | Didriks from Cambridge, MA, USA | CC BY 2.0 | [Commons](https://commons.wikimedia.org/wiki/File:Friedr_Dick_sharpening_knife_on_grinding_wheel.jpeg) |
| `arreglo-ropa` | A sewing studio at CBCC and CIHE.jpg | Peachyeung316 | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:A_sewing_studio_at_CBCC_and_CIHE.jpg) |
| `arroz` | 09703jfCuisine Foods Fruits Philippines Baliuag Bulacanfvf 30.jpg | Judgefloro | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:09703jfCuisine_Foods_Fruits_Philippines_Baliuag_Bulacanfvf_30.jpg) |
| `azucar-refino` | AcucarCubosDSC6096.jpg | CostaPPPR | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:AcucarCubosDSC6096.jpg) |
| `cafe-mezcla` | Coarsely grounded coffee beans in white bowl.png | Alorin | CC BY 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Coarsely_grounded_coffee_beans_in_white_bowl.png) |
| `cake` | 004 1024x944.jpg | Akansha 10 | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:004_1024x944.jpg) |
| `caramelos` | Sweets ..By G,Russ.jpg | Russty67 | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Sweets_..By_G,Russ.jpg) |
| `champu` | Dove Shampoo and Acondicionador.jpg | Jorge Barrios (obra derivada de Bea o) | Public domain | [Commons](https://commons.wikimedia.org/wiki/File:Dove_Shampoo_and_Acondicionador.jpg) |
| `cigarros-fuertes` | Carrolls Number 1.jpg | O'Dea | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Carrolls_Number_1.jpg) |
| `cloro` | Eau-de-Javel-deutsch-3.jpg | Chianti | CC BY-SA 3.0 | [Commons](https://commons.wikimedia.org/wiki/File:Eau-de-Javel-deutsch-3.jpg) |
| `comino` | Whole Cumin Seeds.jpg | Misterneedlemouse | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Whole_Cumin_Seeds.jpg) |
| `corte-pelo` | 11th MEU 141006-M-QH793-013 (15485074205).jpg | U.S. Naval Forces Central Command/U.S. Fifth Fleet | CC BY 2.0 | [Commons](https://commons.wikimedia.org/wiki/File:11th_MEU_141006-M-QH793-013_(15485074205).jpg) |
| `desodorante` | 20-06-18-Deodorants RRK6884.jpg | Ralf Roletschek | CC BY-SA 1.0 fi | [Commons](https://commons.wikimedia.org/wiki/File:20-06-18-Deodorants_RRK6884.jpg) |
| `detergente-polvo` | 20181028 Detergents in Supermarket in Lodz, Poland. October 2018.jpg | Rakoon | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:20181028_Detergents_in_Supermarket_in_Lodz,_Poland._October_2018.jpg) |
| `entrega-domicilio` | A Deliveroo cyclist in London, UK.jpg | Môsieur J. [version 9.1] from Rouen, FRANCE | CC BY 2.0 | [Commons](https://commons.wikimedia.org/wiki/File:A_Deliveroo_cyclist_in_London,_UK.jpg) |
| `frijoles-negros` | Liat Portal for Foodie Disorder - Hamin (Cholent) cooked overnight for Shabbat.jpg | HaJunkiyada | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Liat_Portal_for_Foodie_Disorder_-_Hamin_(Cholent)_cooked_overnight_for_Shabbat.jpg) |
| `galletas-dulces` | 2026-01-06 22 09 06 Molasses cookies in Westampton Township, Burlington County, New Jersey.jpg | Famartin | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:2026-01-06_22_09_06_Molasses_cookies_in_Westampton_Township,_Burlington_County,_New_Jersey.jpg) |
| `jabon-bano` | Cold Cream Soap Hilko, soap bar, pic2.JPG | Alf van Beem | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Cold_Cream_Soap_Hilko,_soap_bar,_pic2.JPG) |
| `jugo-mango` | Rubicon mango drink.jpg | Mx. Granger | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Rubicon_mango_drink.jpg) |
| `lavado-ropa` | - panoramio - Gizerlerbosch (1).jpg | Gizerlerbosch | CC BY 3.0 | [Commons](https://commons.wikimedia.org/wiki/File:-_panoramio_-_Gizerlerbosch_(1).jpg) |
| `leche-entera` | -2022-02-09 A 2 Litre carton of skimmed milk, Trimingham, Norfolk (1).JPG | Kolforn | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:-2022-02-09_A_2_Litre_carton_of_skimmed_milk,_Trimingham,_Norfolk_(1).JPG) |
| `malta` | Barbican pineapple flavour.jpg | JIP | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Barbican_pineapple_flavour.jpg) |
| `manicura` | 20052024-20240520 PAOLA MANICURA (3).jpg | L. Vadillo - MaLéPhotoSpain | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:20052024-20240520_PAOLA_MANICURA_(3).jpg) |
| `pan-suave` | Sliced loaf of sourdough bread.jpg | Nutrition, Food Safety &amp; Health | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Sliced_loaf_of_sourdough_bread.jpg) |
| `pasta-dental` | 01828jfAtom Atomy toothpaste propolisfvf 06.jpg | Judgefloro | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:01828jfAtom_Atomy_toothpaste_propolisfvf_06.jpg) |
| `picadillo` | Beef in pack.jpg | daSupremo | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Beef_in_pack.jpg) |
| `planchado` | Ironing in garment industry.jpg | Surya palanisami | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Ironing_in_garment_industry.jpg) |
| `pollo-congelado` | -2021-10-17 A prepared chicken ready to roast, Trimingham, Norfolk.JPG | Kolforn | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:-2021-10-17_A_prepared_chicken_ready_to_roast,_Trimingham,_Norfolk.JPG) |
| `pure-tomate` | Artashat Cannery 03922.jpg | Narek75 | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Artashat_Cannery_03922.jpg) |
| `queso-blanco` | Queso fresco.JPG | Geoff | CC BY-SA 3.0 | [Commons](https://commons.wikimedia.org/wiki/File:Queso_fresco.JPG) |
| `refresco-lata` | 222mL Can of Coca-Cola Y3000.jpg | BrokenSegue | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:222mL_Can_of_Coca-Cola_Y3000.jpg) |
| `reparacion-calzado` | Capri - 7224.jpg | Jorge Royan | CC BY-SA 3.0 | [Commons](https://commons.wikimedia.org/wiki/File:Capri_-_7224.jpg) |
| `sal-paquete` | Table salt with salt shaker V1.jpg | Poyraz 72 | CC BY-SA 4.0 | [Commons](https://commons.wikimedia.org/wiki/File:Table_salt_with_salt_shaker_V1.jpg) |
| `salchichas` | Grilling Sausages (Unsplash).jpg | The Digital Marketing Collaboration thedmcsa | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Grilling_Sausages_(Unsplash).jpg) |
| `sardinas` | 2006 sardines can open top.jpg | Rl | CC BY-SA 3.0 | [Commons](https://commons.wikimedia.org/wiki/File:2006_sardines_can_open_top.jpg) |
| `vinagre` | Aceite-vinagre.jpg | El Caminito | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Aceite-vinagre.jpg) |
| `yogurt-natural` | Plain Yogurt.jpg | Alabama Extension | CC0 | [Commons](https://commons.wikimedia.org/wiki/File:Plain_Yogurt.jpg) |

Regenerar/actualizar este listado es trabajo de desarrollo (buscar, elegir y volver a llenar la tabla); el detalle
del procedimiento y de las herramientas usadas está en `docs/HISTORIAL_DESARROLLO.md` (entrada del 2026-10-18).
