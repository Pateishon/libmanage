"""Resume un registro JFR exportado con `jfr print --stack-depth 64 --events jdk.ExecutionSample`."""
import collections, re, sys
texto = open(sys.argv[1], encoding="utf-8").read()
eventos = texto.split("jdk.ExecutionSample")[1:]
metodos, hilos = collections.Counter(), collections.Counter()
for e in eventos:
    h = re.search(r'sampledThread = "([^"]*)"', e)
    nombre = h.group(1) if h else "?"
    hilos["virtual/ForkJoinPool" if nombre.startswith("ForkJoinPool") or nombre == "" else nombre] += 1
    for m in set(re.findall(r"^\s+(cl\.libmanage[\w.$]+\.[\w$<>]+)\(", e, re.M)):
        metodos[m] += 1
n = len(eventos)
print(f"Muestras de CPU: {n}")
print("Hilos:")
for h, c in hilos.most_common(5):
    print(f"  {c / n * 100:5.1f}%  {h}")
print("Métodos propios (tiempo inclusivo):")
for m, c in metodos.most_common(10):
    print(f"  {c / n * 100:5.1f}%  {m}")
