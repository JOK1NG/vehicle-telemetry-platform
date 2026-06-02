import type { VehicleUpdateData } from '../../types';

export type MarkerData = VehicleUpdateData & { lastTs?: number | string };

export function createMarkerEl(v: MarkerData, isSelected: boolean): HTMLDivElement {
  const el = document.createElement('div');
  el.className = 'vehicle-marker' + (isSelected ? ' is-selected' : '');
  el.dataset.vehicleId = String(v.vehicleId);
  el.innerHTML = `
    <div class="vehicle-marker__arrow" style="transform: rotate(${v.heading}deg)"></div>
    <div class="vehicle-marker__circle"></div>
    <div class="vehicle-marker__label">${v.plateNo}</div>
  `;
  return el;
}

export function updateMarkerEl(el: HTMLDivElement, v: MarkerData, isSelected: boolean): void {
  el.className = 'vehicle-marker' + (isSelected ? ' is-selected' : '');
  const arrow = el.querySelector('.vehicle-marker__arrow') as HTMLDivElement | null;
  if (arrow) arrow.style.transform = `rotate(${v.heading}deg)`;
  const label = el.querySelector('.vehicle-marker__label');
  if (label && label.textContent !== v.plateNo) label.textContent = v.plateNo;
}
