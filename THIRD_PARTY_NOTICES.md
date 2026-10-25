# Third-party notices

Original TenkeiTech code and assets are Copyright (c) 2026 TenkeiTech and licensed under
[GPL-3.0-only](LICENSE), except where a source file or this notice states otherwise.

## Adapted code

| Project | Use in TenkeiTech | Copyright | License |
|---|---|---|---|
| [GT5-Unofficial](https://github.com/GTNewHorizons/GT5-Unofficial) (`5.09.54.105`, `5.09.54.133`) | ME crafting input buffer, proxy and GUI adaptations; resonating pattern provider guard. | GT5-Unofficial contributors; 2026 TenkeiTech | [LGPL-3.0-only](LICENSES/LGPL-3.0-only.txt) |
| [Applied Energistics 2 GTNH](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/tree/rv3-beta-1050-GTNH) | Resonating interface lifecycle, automatic extraction interface/upgrade/container/GUI hooks, scoped blocking-filter interface/upgrade/slot hooks, and interface upgrade tooltip hooks adapted from `DualityInterface`, `GuiUpgradeable` and related classes. | Applied Energistics 2 contributors; 2026 TenkeiTech | [LGPL-3.0-only](LICENSES/LGPL-3.0-only.txt) |
| [ConstructionWand](https://github.com/Theta-Dev/ConstructionWand/tree/15089d96dd04c26b9110b44388a6133598d744d7) | Universal Wand building, destruction and undo adapted for Minecraft 1.7.10. | 2021 ThetaDev; 2026 TenkeiTech | [MIT](LICENSES/ConstructionWand-MIT.txt) |
| [AE2-PowerTools](https://github.com/Aedial/AE2-PowerTools/tree/be56992b0ab8e64cf254f603dad22cca2931aa6b) | Better ME Requester logic, GUI and Waila status display. | 2026 Aedial; 2026 TenkeiTech | [MIT](LICENSES/AE2-PowerTools-MIT.txt) |

## Adapted textures

The following textures derive from Applied Energistics 2 GTNH `rv3-beta-1050-GTNH`.
Copyright: Applied Energistics 2 contributors and 2026 TenkeiTech. License: [LGPL-3.0-only](LICENSES/LGPL-3.0-only.txt).

| TenkeiTech texture | AE2 source and changes |
|---|---|
| `assets/tenkeitech/textures/items/resonating_pattern.png` | `ItemEncodedPattern.png`, recolored green. |
| `assets/tenkeitech/textures/items/resonating_binding_card.png` | `ToolMemoryCard.png`, combined with a scaled resonating pattern icon. |
| `assets/tenkeitech/textures/items/auto_extraction_card.png` | `ItemMaterial.CardFuzzy.png` long upgrade-card frame and contacts, scaled 2x with nearest-neighbor sampling; center replaced with a black return arrow. |
| `assets/tenkeitech/textures/items/blocking_filter_card_precise.png` | Same long upgrade-card base; center replaced with blocking/pass-through arrows, a gray barrier and a small red N. |
| `assets/tenkeitech/textures/items/blocking_filter_card_fuzzy.png` | Same as the precise variant, with only the N pixels changed from red to green. |

TenkeiTech paths are relative to `src/main/resources/` or the jar root;
AE2 source textures are under `assets/appliedenergistics2/textures/items/`.

Full license texts are in `LICENSE` and `LICENSES/` and are included in distributed jars.

Four GUI backgrounds in `assets/tenkeitech/textures/guis/` are copied unchanged from the same filenames under
`assets/ae2powertools/textures/guis/` at the AE2-PowerTools commit above: `maintainer_gui.png`,
`maintainer_gui_tall.png`, `maintainer_modal.png` and `recipe_selector.png`.
Copyright (c) 2026 Aedial. License: [MIT](LICENSES/AE2-PowerTools-MIT.txt).

The three interface upgrade cards use dedicated 32x32 RGBA textures derived from AE2's long upgrade-card frame, with no runtime tint. Their PNG metadata also records the source, copyright and license.
AE2FluidCraft Rework `1.5.106-gtnh` remains an external dependency; the fluid-interface Mixin only wraps its existing insertion calls.
The Better ME Requester references its textures at runtime; no AE2FC textures are copied or bundled.
Functional ports and references are credited in [README.md](README.md).
