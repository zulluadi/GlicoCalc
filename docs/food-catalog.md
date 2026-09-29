# Default food catalog

Carbohydrates in the default catalog are **available carbohydrate, excluding fibre**, in grams per 100 g of food or beverage. Values are rounded to one decimal place. Preparation and serving details in names matter: raw and cooked foods, sweetened and unsweetened drinks, and packaged dishes can have different values.

Default food names are stored and synchronized in English. Romanian names are display translations keyed to those defaults. During upgrade, untouched defaults previously stored in Romanian are migrated to their English canonical names; user-created and user-edited foods keep the names entered by the user.

Each `InitialFood.sourceRef` identifies the official food record used for its value:

- `Ciqual-2025:<code>`: [ANSES Ciqual 2025](https://zenodo.org/records/17550133), `Carbohydrates (g/100 g)` in the English food composition workbook. The [official methodology](https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202025%20doc%20ENG_2025_11_19.pdf) confirms that this excludes fibre.
- `CoFID-2021:<code>`: [UK Composition of Foods Integrated Dataset 2021](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid), `CHO (g)` in the provisional workbook. The [CoFID user guide](https://assets.publishing.service.gov.uk/media/60538e66d3bf7f03249bac58/McCance_and_Widdowsons_Composition_of_Foods_integrated_dataset_2021.pdf) defines this as available carbohydrate, excluding fibre, calculated as monosaccharide equivalents. CoFID and Ciqual methods may therefore give different numbers for comparable foods.

The 2026 catalog contains 370 foods. Of these, 318 have a matching official record and a `sourceRef`. A `sourceRef` is omitted from the remaining 52: their older estimates are preserved because the available official tables do not identify an equivalent product or recipe precisely enough to justify replacing the value. This especially affects Romanian regional dishes and cheeses, mixed foods, and brand-dependent products. These entries should be checked against a recipe or product label before relying on their number:

Covrig; Hrișcă (fiartă); Mămăligă (moale); Pané (pesmet/ou); Ardei iute (murat); Ciupercă murată; Urzică; Lobodă; Ștevie; Măslină uscată; Portocală Sanguinello; Căpșună (conservă); Curmală / Smochină (uscată); Coacăză albă; Lonicera; Măceașă; Brânză de Burduf; Brânză de Burduf (afumată); Brânză de Secărcău; Brânză de Năsal; Brânză de Coțofen; Brânză de Măgura; Brânză de Cărbune; Carne (Porc, Vită, Pasăre); Pește; Glucoză; Pufuleț; Plăcintă cu brânză; Plăcintă dobrogeană; Cozonac; Ciorbă de legume; Ciorbă de burtă; Borș; Ciorbă de perișoare; Salată de vinete; Zacuscă de pește; Zacuscă de legume; Mâncare de cartofi; Ghiveci; Ciorbă de fasole; Iahnie de fasole; Mămăligă cu brânză; Pârjoală; Ostropel; Tochitură; Cafea / Ceai (fără zahăr); Latte macchiato; Pâine cu maia; Fasole neagră (fiartă); Iaurt natural (fără lactoză); Supă cremă de legume; Salată de boeuf.

Original default remote keys and the original 227-food insertion order remain stable. On upgrade, only a default whose name and carbohydrate value exactly match a previous bundled version is updated. User edits, deletions, packaged foods, and custom foods are retained. New international foods receive new remote keys.

## Glycemic index indicator

The catalog stores an optional `glycemicIndexLevel` for default foods with a close tested equivalent. The UI uses the internationally accepted glucose scale:

- Low: GI 55 or less
- Medium: GI 56–69
- High: GI 70 or more

The classifications are based on the [University of Sydney international GI database](https://glycemicindex.com/gi-search/), the [2021 International Tables of Glycemic Index and Glycemic Load](https://ajcn.nutrition.org/article/S0002-9165%2822%2900494-4/fulltext), and the [Diabetes Canada GI guide](https://guidelines.diabetes.ca/GuideLines/media/Docs/Patient%20Resources/glycemic-index-food-guide.pdf). The database records the category rather than suggesting a single exact GI number for every variety.

GI depends on variety, ripeness, processing, cooking, and recipe. The indicator is therefore omitted for foods with too little available carbohydrate for a meaningful GI test, foods without a close tested equivalent, custom foods, and defaults edited by the user. A missing indicator does not mean a GI of zero. GI describes carbohydrate quality and does not account for serving size; carbohydrate amount remains necessary when estimating a meal's glucose effect.
