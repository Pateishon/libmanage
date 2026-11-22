# Guía de pruebas de aceptación con usuarios finales (UAT)

Complementa las pruebas automatizadas de `e2e/test_aceptacion.py`: aquellas comprueban que se cumplen
los criterios de aceptación; esta sesión comprueba que **personas reales** pueden usar el sistema.

## Participantes y preparación
- 5 participantes: 3 estudiantes, 1 docente y 1 persona de biblioteca (rol administrador).
- Servidor de demostración: `LIBMANAGE_ITERACIONES=10000 java -cp build/classes cl.libmanage.web.Servidor`
  y abrir `http://127.0.0.1:8080`. Cada participante usa su propia cuenta de prueba (`lectorN@uautonoma.cl` / `Lector2026!`).
- El moderador no ayuda salvo que la persona quede bloqueada más de 2 minutos (se registra como "con ayuda").

## Tareas (una por historia de usuario)
| Tarea | Historia | Criterio de éxito |
|---|---|---|
| T1. Encuentra libros de Informática publicados entre 2008 y 2011 que contengan "software". | HU-01 | Llega a los 2 resultados en < 2 min |
| T2. Pide prestado "Cálculo de una variable" y luego devuélvelo. | HU-02 | Ve el mensaje de confirmación y el estado DEVUELTO |
| T3. Revisa qué te recomienda el sistema y explica por qué crees que te lo sugiere. | HU-03 | Encuentra la sección y la entiende |
| T4. Ingresa con una contraseña equivocada y luego con la correcta. | HU-04 | Entiende el mensaje y logra ingresar |
| T5 (administrador). Registra un libro nuevo con 2 ejemplares y búscalo. | HU-05 | El libro aparece en la búsqueda |
| T6. Repite T1 desde tu teléfono. | HU-06 | Completa la tarea sin hacer zoom |

## Registro por participante
| Participante | T1 | T2 | T3 | T4 | T5 | T6 | Tiempo total | Comentarios |
|---|---|---|---|---|---|---|---|---|
| P1 | | | | | | | | |

Marcar: ✔ sin ayuda · ◐ con ayuda · ✘ no lo logró.

## Cuestionario SUS (System Usability Scale)
Escala de 1 (muy en desacuerdo) a 5 (muy de acuerdo):
1. Creo que me gustaría usar este sistema con frecuencia.
2. Encontré el sistema innecesariamente complejo.
3. El sistema me pareció fácil de usar.
4. Creo que necesitaría ayuda de un técnico para poder usarlo.
5. Las funciones del sistema están bien integradas.
6. Hay demasiada inconsistencia en el sistema.
7. La mayoría de las personas aprendería a usarlo muy rápido.
8. El sistema me resultó muy engorroso.
9. Me sentí seguro usando el sistema.
10. Tuve que aprender muchas cosas antes de poder usarlo.

Puntaje: para las preguntas impares se resta 1 a la respuesta; para las pares se calcula 5 − respuesta.
La suma se multiplica por 2,5 (resultado de 0 a 100). **Criterio de aceptación: tasa de éxito ≥ 90 % de
las tareas sin ayuda y SUS promedio ≥ 68** (promedio de referencia de la escala).

## Cierre
Los defectos detectados se registran como *issues* en GitHub con la etiqueta `uat`, se priorizan y, al
corregirlos, se agrega una prueba automatizada que los cubra (como ocurrió con el defecto U1).
