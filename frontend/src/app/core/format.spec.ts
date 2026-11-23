import { daysLeft, durationLabel, expiryLabel, expirySentence, formatSize } from './format';

describe('formatSize', () => {
  it('affiche les octets, Ko, Mo et Go à la française', () => {
    expect(formatSize(1)).toBe('1 octet');
    expect(formatSize(512)).toBe('512 octets');
    expect(formatSize(1536)).toBe('1,5 Ko');
    expect(formatSize(2.6 * 1024 * 1024)).toBe('2,6 Mo');
    expect(formatSize(300_000_000)).toBe('286 Mo');
    expect(formatSize(1024 ** 3)).toBe('1 Go');
  });
});

describe('expiration', () => {
  const now = new Date('2026-11-06T10:00:00');

  it('compte les jours calendaires restants', () => {
    expect(daysLeft('2026-11-06T23:00:00', now)).toBe(0);
    expect(daysLeft('2026-11-07T08:00:00', now)).toBe(1);
    expect(daysLeft('2026-11-09T10:00:00', now)).toBe(3);
  });

  it('produit les libellés de l’historique', () => {
    expect(expiryLabel('2026-11-06T09:00:00', now)).toBe('Expiré');
    expect(expiryLabel('2026-11-06T20:00:00', now)).toBe("Expire aujourd'hui");
    expect(expiryLabel('2026-11-07T20:00:00', now)).toBe('Expire demain');
    expect(expiryLabel('2026-11-08T20:00:00', now)).toBe('Expire dans 2 jours');
  });

  it('produit la phrase de la page de téléchargement', () => {
    expect(expirySentence('2026-11-06T20:00:00', now)).toBe("Ce fichier expirera aujourd'hui.");
    expect(expirySentence('2026-11-07T20:00:00', now)).toBe('Ce fichier expirera demain.');
    expect(expirySentence('2026-11-09T20:00:00', now)).toBe('Ce fichier expirera dans 3 jours.');
  });

  it('nomme la durée de conservation choisie', () => {
    expect(durationLabel(1)).toBe('une journée');
    expect(durationLabel(7)).toBe('une semaine');
    expect(durationLabel(12)).toBe('12 jours');
  });
});
