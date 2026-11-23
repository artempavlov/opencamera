"""Configure a fresh copy of the Open Camera FPS tree as Open Camera FPS 2."""
import argparse
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('copy', type=Path)
app = parser.parse_args().copy / 'app'
package = 'net.sourceforge.opencamera.pjd110.second'
main = app / 'src/main'
res = main / 'res'
java = main / 'java/net/sourceforge/opencamera'
edits = [
    (app/'build.gradle', 'applicationId "net.sourceforge.opencamera.pjd110"', f'applicationId "{package}"', 1),
    (app/'build.gradle', 'testApplicationId "net.sourceforge.opencamera.test"', f'testApplicationId "{package}.test"', 1),
    (res/'values/pjd.xml', '>Open Camera FPS<', '>Open Camera FPS 2<', 1),
    (res/'xml-v25/shortcuts.xml', 'targetPackage="net.sourceforge.opencamera"', f'targetPackage="{package}"', 5),
    (res/'xml/preferences_sub_remote_ctrl.xml', 'targetPackage="net.sourceforge.opencamera"', f'targetPackage="{package}"', 1),
    (res/'xml/preferences_sub_camera_controls_more.xml', 'defaultValue="OpenCamera"', 'defaultValue="OpenCameraFPS2"', 1),
    *((java/file, '"OpenCamera"', '"OpenCameraFPS2"', count) for file, count in
      [('SaveLocationHandler.java', 2), ('MyPreferenceFragment.java', 1), ('StorageUtils.java', 1)]),
]
contents = {}
for path, old, new, count in edits:
    s = contents.get(path, path.read_text())
    if s.count(old) != count:
        parser.error(f'unexpected source: {path} / {old}')
    contents[path] = s.replace(old, new)
for path, s in contents.items():
    path.write_text(s)
