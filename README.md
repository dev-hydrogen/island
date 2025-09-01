
Island++
---
Client-side utility mod for [MCC Island](https://mcchampionship.com/island/). Fork of [Trident](https://github.com/pe3ep/Trident) adding a lot of extra features and additions, especially to fishing

## Requirements
- Minecraft 1.21.8
- Fabric
- [Yet Another Config Lib](https://modrinth.com/mod/yacl)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- [Noxesium](https://modrinth.com/mod/noxesium)

## Island++ Features

### Hook, Magnet, Rod Chances & Chance perks modules

Each row shows the Base view (left) and the Expanded view (right) of a module.

All the modules calculations are affected by your current spot, tides, upgrades, augments, supplies, and anything else that should affect them. 

<table>
  <thead>
    <tr>
      <th align="center">Base</th>
      <th align="center">Expanded</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><img alt="Module base view 1" src="https://github.com/user-attachments/assets/868e755a-1366-416e-bb23-b251b7fdea05" width="100%" /><br/><sub>Base view — compact overview</sub></td>
      <td align="center"><img alt="Module expanded view 1" src="https://github.com/user-attachments/assets/34256229-281e-4d9f-ba0f-b747ce6c446c" width="100%" /><br/><sub>Expanded view — Shows calculations.</sub></td>
    </tr>
    <tr>
      <td align="center"><img alt="Module base view 2" src="https://github.com/user-attachments/assets/b3966547-7649-409f-a519-50a93ece8e91" width="100%" /><br/><sub>Base view — compact overview</sub></td>
      <td align="center"><img alt="Module expanded view 2" src="https://github.com/user-attachments/assets/35fa9c45-6ba0-4615-bcf6-540a28b7dcc1" width="100%" /><br/><sub>Expanded view — Shows calculations, and the % chance to get 1 more</sub></td>
    </tr>
    <tr>
      <td align="center"><img alt="Module base view 3" src="https://github.com/user-attachments/assets/ad012764-01fb-415e-9b1b-744b668fab3c" width="100%" /><br/><sub>Base view — compact overview</sub></td>
      <td align="center"><img alt="Module expanded view 3" src="https://github.com/user-attachments/assets/cd140e0d-724d-4fd9-bc72-c9bb5f91b3be" width="100%" /><br/><sub>Expanded view — Shows calculations. Also works in grotto spots!</sub></td>
    </tr>
  </tbody>
  <tfoot>
    <tr>
      <td colspan="2" align="center"><sub>Use the dropdowns to view detailed breakdowns, and use the compact view to see useful info at a glance.</sub></td>
    </tr>
  </tfoot>
  
</table>

<p align="center">
  <img alt="Additional module view" src="https://github.com/user-attachments/assets/3b1103f7-2000-4509-abed-8c6bddb42438" width="70%" />
  <br/>
  <sub>Rod chances also included</sub>
  
</p>

### Upgraded Supplies module

<table>
  <caption><strong>Upgraded Supplies module</strong></caption>
  <tbody>
    <tr>
      <td align="center" width="60%">
        <img alt="Upgraded Supplies module" src="https://github.com/user-attachments/assets/e42b340c-80c6-464e-a1ef-4f65a583b16a" width="100%" />
      </td>
      <td width="40%">
        <ul>
          <li>Colorcoded bait and line!</li>
          <li>Shows supplies, augments, and overclocks</li>
          <li>Displays OC levels</li>
          <li>Augment uses: max and remaining</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>

### Upgrades module

<table>
  <caption><strong>Upgrades module</strong></caption>
  <tbody>
    <tr>
      <td align="center" width="60%">
        <img alt="Upgrades module" src="https://github.com/user-attachments/assets/3054d3da-4d5d-469b-9fd4-f0436ade2fab" width="100%" />
      </td>
      <td width="40%">
        <ul>
          <li>Summarizes your current upgrade status</li>
          <li>Quick at-a-glance progress</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>

### Spot module

<table>
  <caption><strong>Spot module</strong></caption>
  <tbody>
    <tr>
      <td align="center" width="60%">
        <img alt="Spot module" src="https://github.com/user-attachments/assets/87d6bb29-ac53-4c6f-927a-8c8556c77ac6" width="100%" />
      </td>
      <td width="40%">
        <ul>
          <li>Displays all buffs from the current spot</li>
          <li>Understand context-specific bonuses quickly</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>


## Base Trident Features

<table>
  <caption><strong>Base Trident Features</strong></caption>
  <thead>
    <tr>
      <th align="left">Feature</th>
      <th align="center">Preview</th>
      <th align="left">Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Rarity overlay</strong></td>
      <td align="center">—</td>
      <td>Displays an outline behind the item in the color of its rarity</td>
    </tr>
    <tr>
      <td><strong>Blueprint indicators</strong></td>
      <td align="center"><img alt="Blueprint indicators" src="https://cdn.modrinth.com/data/cached_images/5cf23263586928c33fc938b77ae733ddf2ab0731.png" width="100%" /></td>
      <td>
        Displays icons based on blueprint ownership status.<br/>
        Tick icon — Blueprint has maxed royal donations.<br/>
        Diamond icon — Blueprint is not owned by player.
      </td>
    </tr>
    <tr>
      <td><strong>Focus game on countdown</strong></td>
      <td align="center">—</td>
      <td>Puts Minecraft in front when your game is about to start</td>
    </tr>
    <tr>
      <td><strong>Kill Feed</strong></td>
      <td align="center"><img alt="Kill feed" src="https://cdn.modrinth.com/data/cached_images/7f9a0186174f124c7e944250b7eae64ab8a93c82.png" width="100%" /></td>
      <td>Adds a Kill Feed similar to Counter‑Strike and Valorant for Battle Box and Dynaball</td>
    </tr>
    <tr>
      <td><strong>Questing Module</strong></td>
      <td align="center"><img alt="Questing widget" src="https://cdn.modrinth.com/data/cached_images/7452f924368eab780a33f697e74aec2194438ddf.png" width="100%" /></td>
      <td>Adds a Questing Module widget with <em>live‑updating</em> progress</td>
    </tr>
    <tr>
      <td><strong>Fishing Supplies Module</strong></td>
      <td align="center"><img alt="Supplies module" src="https://cdn.modrinth.com/data/cached_images/f9b49d912cd8016d88c0a8aff634838791cbcfdd.png" width="100%" /></td>
      <td>Shows Bait, line durability, active Overclocks, and equipped Augments in Fishtances</td>
    </tr>
  </tbody>
</table>

<p><em>And many more! You can see an up‑to‑date list of features in the mod's config.</em></p>

*Not affiliated and endorsed by Noxcrew or Mojang AB*
