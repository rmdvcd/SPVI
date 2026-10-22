import sys
sys.path.insert(0, "tools/seed_demo")
import corregir_fotos as c

c.OBJETIVOS = {
    "serv06": [("laminator office", ("laminat",)), ("lamination paper", ("laminat",))],
    "serv10": [("hair cutting scissors", ("hair", "scissor")), ("barber cutting hair", ("barber", "hair")), ("haircut", ("haircut", "hair"))],
    "serv12": [("manicure nails", ("manicure", "nail")), ("nail polish hands", ("nail",)), ("manicure", ("manicure",))],
}
c.main()
