"""V4 - summarise comparison.csv written by run_comparison.groovy.

Usage: python compare.py <scene directory> [<summary.md>]

Prints, per scene and distance mode, the largest |OPA - DiAna| and, for the
ball pairs, the largest |tool - analytic| against spheres_truth.csv. The pass
line for centre-centre and centre-edge is one voxel diagonal.
"""
import csv
import math
import sys

directory = sys.argv[1].rstrip('/\\') + '/'
summary_path = sys.argv[2] if len(sys.argv) > 2 else None
VOXEL = (0.2, 0.2, 0.5)
DIAGONAL = math.sqrt(sum(v * v for v in VOXEL))
MODES = ['centre_centre', 'centre_edge', 'edge_centre', 'edge_edge']

rows = list(csv.DictReader(open(directory + 'comparison.csv')))
truth = {int(r['pair']): r for r in csv.DictReader(open(directory + 'spheres_truth.csv'))}
truth_column = {'centre_centre': 'centre_centre_um', 'centre_edge': 'centre_edge_A_to_B_um',
                'edge_centre': 'edge_centre_A_to_B_um', 'edge_edge': 'edge_edge_um'}

lines = []
def say(text=''):
    lines.append(text)
    print(text)

say('Voxel 0.2 x 0.2 x 0.5 um; voxel diagonal %.3f um.' % DIAGONAL)
say('DiAna rows come from: ' + ', '.join(sorted({r['source'] for r in rows if r['tool'] == 'DiAna'})))
say()
say('| Scene | Mode | max abs(OPA - DiAna) um | mean (DiAna - OPA) um | max abs(OPA - truth) um | max abs(DiAna - truth) um | partners agree |')
say('|---|---|---|---|---|---|---|')
for scene in ['spheres', 'blobs']:
    opa = {int(r['label_A']): r for r in rows if r['scene'] == scene and r['tool'] == 'OPA'}
    diana = {int(r['label_A']): r for r in rows if r['scene'] == scene and r['tool'] == 'DiAna'}
    labels = sorted(set(opa) & set(diana))
    partners = all(opa[l]['partner_B'] == diana[l]['partner_B'] for l in labels)
    for mode in MODES:
        diffs = [float(diana[l][mode]) - float(opa[l][mode]) for l in labels]
        max_abs = max(abs(d) for d in diffs)
        mean = sum(diffs) / len(diffs)
        t_opa = t_diana = '-'
        if scene == 'spheres':
            t_opa = '%.3f' % max(abs(float(opa[l][mode]) - float(truth[l][truth_column[mode]])) for l in labels)
            t_diana = '%.3f' % max(abs(float(diana[l][mode]) - float(truth[l][truth_column[mode]])) for l in labels)
        say('| %s | %s | %.3f | %+.3f | %s | %s | %s |' % (
            scene, mode.replace('_', '-'), max_abs, mean, t_opa, t_diana,
            'yes' if partners else 'no'))
say()
say('Per-object values (um):')
say()
say('| Scene | A | B | Tool | centre-centre | centre-edge | edge-centre | edge-edge | contact |')
say('|---|---|---|---|---|---|---|---|---|')
for r in rows:
    say('| %s | %s | %s | %s | %.3f | %.3f | %.3f | %.3f | %s %s |' % (
        r['scene'], r['label_A'], r['partner_B'], r['tool'], float(r['centre_centre']),
        float(r['centre_edge']), float(r['edge_centre']), float(r['edge_edge']),
        ('%.3f' % float(r['contact'])).rstrip('0').rstrip('.'), r['contact_unit']))
if summary_path:
    open(summary_path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
