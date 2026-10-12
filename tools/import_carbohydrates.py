from __future__ import annotations

import argparse
import json
import logging
import re
import warnings
from pathlib import Path

from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PDF = ROOT / "manual-contagem-carboidratos-web-1.pdf"
DEFAULT_OUTPUT = ROOT / "carboidratos-data.js"

MEASURE_UNITS = (
    r"colher(?:es)?(?:\s+(?:de|da|do|cheia|cheio|rasa|raso|sobremesa|chá|cha|café|cafe|servir|sopa))*|"
    r"fatias?|unidades?|copos?|xícaras?|xicaras?|taças?|tacas?|porção(?:es)?|porcao(?:es)?|"
    r"pedaço(?:s)?|pedaco(?:s)?|filé(?:s)?|file(?:s)?|dente(?:s)?|folha(?:s)?|lata(?:s)?|garrafa(?:s)?|doses?|"
    r"pacotes?|embalagem(?:s)?|pacotinho(?:s)?|barras?|bolas?|potes?|potinho(?:s)?|conchas?|pratos?|pegador(?:es)?|escumadeira(?:s)?|pires|"
    r"punhado(?:s)?|coquetel(?:s)?|posta(?:s)?|coher(?:es)?|bife(?:s)?|cápsula(?:s)?|capsula(?:s)?|torrada(?:s)?|long\s+neck|"
    r"cuia(?:s)?|quadradinho(?:s)?|quadrado(?:s)?|tulipa(?:s)?|garrafinha(?:s)?|caixinha(?:s)?|frasco(?:s)?|"
    r"sanduíche(?:s)?|sanduiche(?:s)?|coxa(?:s)?|sobrecoxa(?:s)?|peito|asa(?:s)?|ovo(?:s)?|bago(?:s)?|pouch|"
    r"pastilha(?:s)?|metade|picolé(?:s)?|picle(?:s)?|saco(?:s)?|biscoito(?:s)?|cacho(?:s)?|dosador(?:es)?|cálice(?:s)?|calice(?:s)?|"
    r"bombom(?:s)?|saquinho(?:s)?|copinho(?:s)?|mini\s+tubo|pastel(?:s)?|"
    r"canecas?|sachês?|saches?|envelopes?|tabletes?|ramos?|gomos?|rodelas?|caixas?|lâminas?|laminas?|"
    r"gramas?|gr\.?|unid\.?|ml|g"
)
MEASURE_PATTERN = re.compile(
    rf"(?i)(?<!\w)(?:\d+(?:[.,]\d+)?(?:\s*/\s*\d+)?|[½¼¾])\s*(?:{MEASURE_UNITS})\b(?:\s+(?:médio|média|grande|pequeno|pequena|cheia|cheio|rasa|raso|duplo|dupla|inteiro|inteira))*"
)
MEASURE_WITHOUT_COUNT_PATTERN = re.compile(rf"(?i)(?<!\w)(?:{MEASURE_UNITS})\b")
NUMBER_PATTERN = re.compile(r"^\s*(\d+(?:[.,]\d+)?)\s*$")
WEIGHT_PATTERN = re.compile(r"(\d+(?:[.,]\d+)?)\s*$")


def number_from_text(value: str) -> float | None:
    match = NUMBER_PATTERN.match(value)
    return float(match.group(1).replace(",", ".")) if match else None


def extract_page_rows(page, page_number: int) -> list[dict]:
    blocks: list[tuple[float, float, str]] = []

    def collect(text, _cm, text_matrix, _font, _font_size):
        text = text.strip()
        if text:
            blocks.append((float(text_matrix[4]), float(text_matrix[5]), text))

    page.extract_text(visitor_text=collect)
    carb_cells = []
    for x, y, text in blocks:
        value = number_from_text(text)
        if value is not None and 292 <= x < 340:
            carb_cells.append((y, value))
    carb_cells.sort(key=lambda item: item[0], reverse=True)

    rows = []
    for index, (y, carbohydrates) in enumerate(carb_cells):
        boundary_tolerance = 2
        upper = (carb_cells[index - 1][0] + y) / 2 + boundary_tolerance if index else (
            y + (y - carb_cells[index + 1][0]) / 2 + boundary_tolerance
            if index + 1 < len(carb_cells)
            else float("inf")
        )
        lower = (y + carb_cells[index + 1][0]) / 2 + boundary_tolerance if index + 1 < len(carb_cells) else float("-inf")
        row_blocks = [block for block in blocks if 45 <= block[0] < 292 and lower < block[1] <= upper]
        food_column = sorted((block for block in row_blocks if block[0] < 150), key=lambda block: -block[1])
        measure_column = sorted((block for block in row_blocks if block[0] >= 150), key=lambda block: -block[1])
        raw_name = " ".join(block[2] for block in food_column)
        raw_measure = " ".join(block[2] for block in measure_column)
        combined = re.sub(r"\s+", " ", f"{raw_name} {raw_measure}").strip()
        weight_match = WEIGHT_PATTERN.search(combined)
        if not weight_match:
            continue
        reference_amount = float(weight_match.group(1).replace(",", "."))
        label = combined[:weight_match.start()].strip(" \t\n-–")
        if not label or reference_amount <= 0:
            continue

        measure_match = MEASURE_PATTERN.search(label)
        if not measure_match:
            unnumbered_measure = MEASURE_WITHOUT_COUNT_PATTERN.search(label)
            if unnumbered_measure and unnumbered_measure.start() > 0:
                measure_match = unnumbered_measure
        if measure_match:
            name = label[:measure_match.start()].strip(" ,;–-")
            portion = label[measure_match.start():].strip()
        else:
            name = label
            portion = "porção do manual"
        if not name:
            name = label
            portion = "porção do manual"

        rows.append({
            "id": f"p{page_number}-r{index + 1}",
            "name": name,
            "portion": portion,
            "referenceAmount": reference_amount,
            "carbohydrates": carbohydrates,
            "calories": None,
            "sourcePage": page_number,
        })
    return rows


def extract_database(pdf_path: Path) -> tuple[list[dict], int]:
    reader = PdfReader(str(pdf_path))
    all_rows = []
    pages_with_table = 0
    for page_index, page in enumerate(reader.pages):
        page_text = page.extract_text() or ""
        if "Alimento" not in page_text or "CHO (g)" not in page_text:
            continue
        page_rows = extract_page_rows(page, page_index + 1)
        if page_rows:
            pages_with_table += 1
            all_rows.extend(page_rows)
    return all_rows, pages_with_table


def main() -> None:
    parser = argparse.ArgumentParser(description="Importa a tabela de alimentos do manual SBD para uso local no app.")
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--preview", action="store_true", help="Mostra uma amostra e não grava a base.")
    args = parser.parse_args()

    warnings.filterwarnings("ignore")
    logging.disable(logging.WARNING)
    rows, pages = extract_database(args.pdf)
    print(f"Páginas com tabela: {pages}; alimentos extraídos: {len(rows)}")
    for row in rows[:24]:
        print(f"p.{row['sourcePage']}: {row['name']} | {row['portion']} | {row['referenceAmount']:g} g/ml | {row['carbohydrates']:g} g CHO")
    if args.preview:
        return
    payload = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
    args.output.write_text(
        "// Dados extraídos do Manual de Contagem de Carboidratos SBD 2025.\n"
        f"window.CARBOHYDRATE_FOODS = {payload};\n",
        encoding="utf-8",
    )
    print(f"Base gerada: {args.output}")


if __name__ == "__main__":
    main()
