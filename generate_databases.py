import pypdf
import re
import json
import os

pdf_path = r"d:\Documentos\Android Projects\RegistroGlicose\manual-contagem-carboidratos-web-1.pdf"
android_raw_dir = r"d:\Documentos\Android Projects\RegistroGlicose\app\src\main\res\raw"
web_js_dir = r"d:\Documentos\Android Projects\RegistroGlicose\web_version\src\js"

print("Starting database generation...")

if not os.path.exists(pdf_path):
    print(f"Error: PDF manual not found at {pdf_path}")
    exit(1)

reader = pypdf.PdfReader(pdf_path)
start_page = 48
end_page = 153

raw_items = []
current_item_lines = []
num_pattern = re.compile(r'\s+(\d+(?:[\.,]\d+)?)\s+(\d+(?:[\.,]\d+)?)\s+(\d+(?:[\.,]\d+)?)$')

# 1. Parse lines and extract food rows
for page_idx in range(start_page, end_page + 1):
    page = reader.pages[page_idx]
    text = page.extract_text()
    lines = text.split("\n")
    for line in lines:
        line = line.strip()
        if not line or "TABELA DE ALIMENTOS" in line or "Alimento Medida usual" in line or "(kcal)" in line or line.isdigit():
            continue
        
        current_item_lines.append(line)
        match = num_pattern.search(line)
        if match:
            full_text = " ".join(current_item_lines)
            full_match = num_pattern.search(full_text)
            if full_match:
                main_text = full_match.string[:full_match.start()].strip()
                g_ml = float(full_match.group(1).replace(',', '.'))
                cho = float(full_match.group(2).replace(',', '.'))
                kcal = float(full_match.group(3).replace(',', '.'))
                
                # Convert back to int if it has no decimal part
                g_ml = int(g_ml) if g_ml.is_integer() else g_ml
                cho = int(cho) if cho.is_integer() else cho
                kcal = int(kcal) if kcal.is_integer() else kcal
                
                raw_items.append({
                    "raw_text": main_text,
                    "g_ml": g_ml,
                    "cho": cho,
                    "kcal": kcal
                })
            current_item_lines = []

print(f"Extracted {len(raw_items)} raw items.")

# 2. Split Name and Measure
# Common units in Portuguese
units = [
    "colher", "colheres", "fatia", "fatias", "unidade", "unidades", "copo", "copos",
    "pedaço", "pedaços", "porção", "porções", "folha", "folhas", "dente", "dentes",
    "taça", "taças", "bola", "bolas", "lata", "latas", "dose", "doses", "xícara",
    "xícaras", "escumadeira", "escumadeiras", "concha", "conchas", "embalagem",
    "embalagens", "pires", "prato", "ramalhete", "sobremesa", "filé", "filés", "un"
]

digit_unit_regex = re.compile(
    r'\b(\d+(?:/\d+)?\s+(?:' + '|'.join(units) + r'))\b',
    re.IGNORECASE
)

no_digit_regex = re.compile(
    r'\b(' + '|'.join(units) + r')\b',
    re.IGNORECASE
)

def split_food_text(text):
    # Try digit prefix first (e.g. "1 colher", "1/2 copo")
    match = digit_unit_regex.search(text)
    if match:
        idx = match.start()
        name = text[:idx].strip()
        measure = text[idx:].strip()
        # Clean trailing commas/dashes in name
        name = re.sub(r'[\s,\-]+$', '', name)
        return name, measure

    # Fallback to pure unit word
    matches = list(no_digit_regex.finditer(text))
    if matches:
        # Choose the first match
        idx = matches[0].start()
        name = text[:idx].strip()
        measure = text[idx:].strip()
        name = re.sub(r'[\s,\-]+$', '', name)
        return name, measure

    return text, ""

# 3. Clean up the parsed items
cleaned_foods = []
for idx, item in enumerate(raw_items):
    raw_text = item["raw_text"]
    name, measure = split_food_text(raw_text)
    
    # Normalize double spaces and remove special chars
    name = re.sub(r'\s+', ' ', name).strip()
    measure = re.sub(r'\s+', ' ', measure).strip()
    
    # Clean up standard unicode text anomalies if any
    # (pypdf got correct characters, so just basic string sanitation)
    cleaned_foods.append({
        "id": idx + 1,
        "name": name,
        "measure": measure,
        "grams": item["g_ml"],
        "carbs": item["cho"],
        "calories": item["kcal"]
    })

# 4. Save to Android Assets
os.makedirs(android_raw_dir, exist_ok=True)
android_dest = os.path.join(android_raw_dir, "foods.json")
with open(android_dest, "w", encoding="utf-8") as f:
    json.dump(cleaned_foods, f, ensure_ascii=False, indent=2)
print(f"Android database written to: {android_dest} (Size: {os.path.getsize(android_dest)} bytes)")

# 5. Save to Web Client as JavaScript Module
os.makedirs(web_js_dir, exist_ok=True)
web_dest = os.path.join(web_js_dir, "foods.js")
with open(web_dest, "w", encoding="utf-8") as f:
    f.write("// Banco de dados de alimentos para contagem de carboidratos (SBD)\n")
    f.write("export const FOODS = ")
    json.dump(cleaned_foods, f, ensure_ascii=False, indent=2)
    f.write(";\n")
print(f"Web database written to: {web_dest} (Size: {os.path.getsize(web_dest)} bytes)")

print("Database generation completed successfully!")
