import { MAX_FILE_SIZE, fileError } from './upload-rules';

function fakeFile(name: string, size: number): File {
  const file = new File(['x'], name);
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('fileError', () => {
  it('accepte un fichier ordinaire', () => {
    expect(fileError(fakeFile('photo.jpg', 1000))).toBeNull();
    expect(fileError(fakeFile('exactement-1-go.zip', MAX_FILE_SIZE))).toBeNull();
  });

  it('refuse un fichier vide ou de plus de 1 Go', () => {
    expect(fileError(fakeFile('vide.txt', 0))).toBe('Ce fichier est vide.');
    expect(fileError(fakeFile('gros.iso', MAX_FILE_SIZE + 1))).toBe('La taille des fichiers est limitée à 1 Go.');
  });

  it('refuse les extensions interdites, quelle que soit la casse', () => {
    expect(fileError(fakeFile('setup.EXE', 10))).toBe('Les fichiers .exe ne sont pas autorisés.');
    expect(fileError(fakeFile('script.sh', 10))).toBe('Les fichiers .sh ne sont pas autorisés.');
  });
});
