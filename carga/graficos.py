"""Genera el gráfico comparativo de la prueba de carga (v1.0 vs v1.1)."""
import csv, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

filas = [f for f in csv.DictReader(open(sys.argv[1], encoding="utf-8")) if f["operacion"] == "TOTAL"]
colores = {"v1.0": "#eb6834", "v1.1": "#2a78d6"}
nombres = {"v1.0": "v1.0 (inicial)", "v1.1": "v1.1 (corregida)"}
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9, "axes.spines.top": False,
                     "axes.spines.right": False, "axes.edgecolor": "#9aa0a6", "axes.labelcolor": "#3c4043",
                     "xtick.color": "#5f6368", "ytick.color": "#5f6368"})
fig, (a, b) = plt.subplots(1, 2, figsize=(7.2, 2.7), dpi=200)
for v in ["v1.0", "v1.1"]:
    d = [f for f in filas if f["version"] == v]
    u = [int(f["usuarios"]) for f in d]
    a.plot(u, [float(f["p95_ms"]) for f in d], color=colores[v], lw=2, marker="o", ms=4.5, label=nombres[v])
    b.plot(u, [float(f["por_segundo"]) for f in d], color=colores[v], lw=2, marker="o", ms=4.5, label=nombres[v])
a.axhline(500, color="#5f6368", lw=1, ls="--")
a.text(12, 560, "Objetivo p95 = 500 ms", color="#3c4043", fontsize=8)
a.set_yscale("log"); a.set_xscale("log")
a.set_title("Latencia p95 (ms, escala log)", fontsize=9.5, loc="left", color="#202124")
b.set_xscale("log")
b.set_title("Rendimiento (solicitudes por segundo)", fontsize=9.5, loc="left", color="#202124")
for ax in (a, b):
    ax.set_xticks([10, 50, 100, 200, 400]); ax.set_xticklabels(["10", "50", "100", "200", "400"])
    ax.set_xlabel("Usuarios concurrentes")
    ax.grid(axis="y", color="#e8eaed", lw=0.8); ax.set_axisbelow(True)
b.legend(frameon=False, fontsize=8, loc="center right")
fig.tight_layout()
fig.savefig(sys.argv[2], bbox_inches="tight")
