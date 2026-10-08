#!/usr/bin/env python3
"""Comprobaciones estáticas de enlaces, versión y esquema documentado en README.md."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
README = ROOT / "README.md"
ENTITIES = ROOT / "data/src/main/kotlin/cu/spvi/data/db/entity/Entities.kt"
APP_GRADLE = ROOT / "app/build.gradle.kts"


def matching_parenthesis(text: str, opening: int) -> int:
    """Devuelve el cierre correspondiente, ignorando strings y comentarios Kotlin."""
    depth = 0
    i = opening
    state = "normal"
    while i < len(text):
        if state == "line_comment":
            if text[i] == "\n":
                state = "normal"
            i += 1
            continue
        if state == "block_comment":
            if text.startswith("*/", i):
                state = "normal"
                i += 2
            else:
                i += 1
            continue
        if state == "string":
            if text[i] == "\\":
                i += 2
            elif text[i] == '"':
                state = "normal"
                i += 1
            else:
                i += 1
            continue
        if state == "raw_string":
            if text.startswith('"""', i):
                state = "normal"
                i += 3
            else:
                i += 1
            continue

        if text.startswith("//", i):
            state = "line_comment"
            i += 2
        elif text.startswith("/*", i):
            state = "block_comment"
            i += 2
        elif text.startswith('"""', i):
            state = "raw_string"
            i += 3
        elif text[i] == '"':
            state = "string"
            i += 1
        elif text[i] == "(":
            depth += 1
            i += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
            i += 1
        else:
            i += 1
    raise ValueError(f"Paréntesis sin cerrar en el índice {opening}")


def snake_case(name: str) -> str:
    name = re.sub(r"Entity$", "", name)
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def schema_entities() -> dict[str, set[str]]:
    source = ENTITIES.read_text(encoding="utf-8")
    result: dict[str, set[str]] = {}
    for annotation in re.finditer(r"@Entity\b", source):
        opening = source.find("(", annotation.end())
        if opening < 0:
            raise ValueError("@Entity sin parámetros: no se pudo determinar la tabla")
        closing = matching_parenthesis(source, opening)
        args = source[opening + 1 : closing]
        tail = source[closing + 1 :]
        declaration = re.match(r"\s*data\s+class\s+([A-Za-z_]\w*)\s*\(", tail)
        if not declaration:
            raise ValueError("No se encontró data class tras una anotación @Entity")
        class_name = declaration.group(1)
        class_opening = closing + 1 + declaration.end() - 1
        class_closing = matching_parenthesis(source, class_opening)
        params = source[class_opening + 1 : class_closing]
        table_match = re.search(r'tableName\s*=\s*"([^"]+)"', args)
        table = table_match.group(1) if table_match else snake_case(class_name)
        columns = set(re.findall(r"\bval\s+([A-Za-z_]\w*)\s*:", params))
        if not columns:
            raise ValueError(f"No se encontraron columnas para {class_name}")
        if table in result:
            raise ValueError(f"Tabla duplicada en Entities.kt: {table}")
        result[table] = columns
    return result


def markdown_schema(readme: str, expected_tables: set[str]) -> dict[str, set[str]]:
    section = readme.split("## Base de datos Room v11", 1)
    if len(section) != 2:
        raise ValueError("No se encontró el encabezado 'Base de datos Room v11' en README.md")
    rows: dict[str, set[str]] = {}
    for line in section[1].splitlines():
        if not line.lstrip().startswith("|"):
            if rows and line.startswith("## "):
                break
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 4:
            continue
        table = cells[0].strip().strip("`")
        if table not in expected_tables:
            continue
        if table in rows:
            raise ValueError(f"Tabla repetida en la tabla del README: {table}")
        rows[table] = set(re.findall(r"`([A-Za-z_]\w*)`", cells[2]))
    return rows


def main() -> int:
    errors: list[str] = []
    readme = README.read_text(encoding="utf-8")

    for label, target in re.findall(r"\[([^\]]+)\]\(([^)]+)\)", readme):
        target = target.strip()
        if target.startswith(("https://", "http://", "mailto:", "//")):
            continue
        relative = target.split("#", 1)[0]
        if not relative:
            continue
        path = (ROOT / relative).resolve()
        if not path.exists():
            errors.append(f"Enlace roto en README.md: [{label}]({target})")

    gradle = APP_GRADLE.read_text(encoding="utf-8")
    version_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    readme_version = re.search(r"^\|\s*Versión\s*\|\s*\*\*([^*]+)\*\*", readme, re.MULTILINE)
    if not version_match:
        errors.append("No se encontró versionName en app/build.gradle.kts")
    elif not readme_version:
        errors.append("No se encontró la fila de versión en README.md")
    elif version_match.group(1) != readme_version.group(1):
        errors.append(
            f"Versión distinta: README={readme_version.group(1)}, app/build.gradle.kts={version_match.group(1)}"
        )

    try:
        actual = schema_entities()
        documented = markdown_schema(readme, set(actual))
        for table in sorted(set(actual) - set(documented)):
            errors.append(f"Falta en la tabla del README la entidad Room `{table}`")
        for table in sorted(set(documented) - set(actual)):
            errors.append(f"La tabla del README documenta una entidad Room inexistente: `{table}`")
        for table in sorted(set(actual) & set(documented)):
            missing = actual[table] - documented[table]
            extra = documented[table] - actual[table]
            if missing:
                errors.append(f"Columnas de `{table}` no documentadas en README: {', '.join(sorted(missing))}")
            if extra:
                errors.append(f"Columnas de `{table}` que no existen en Entities.kt: {', '.join(sorted(extra))}")
    except (OSError, ValueError) as error:
        errors.append(f"No se pudo cotejar el esquema Room: {error}")

    if errors:
        print("Comprobación estática de documentación: ERROR")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Comprobación estática de documentación: OK")
    print(f"- Enlaces locales del README: revisados")
    print(f"- Versión: {version_match.group(1)}")
    print(f"- Entidades Room y columnas: {len(actual)} tablas cotejadas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
