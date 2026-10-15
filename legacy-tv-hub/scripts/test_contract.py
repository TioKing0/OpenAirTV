from pathlib import Path
from zipfile import ZipFile
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
product = json.loads((root / 'product.json').read_text())
assert product['androidApplicationId'] == 'br.com.legacytvhub.manager'
assert product['orsayWidgetId'] == 'LegacyTVHub'
gradle = (root / 'app/build.gradle').read_text()
assert "applicationId 'br.com.legacytvhub.manager'" in gradle
package = root / 'app/src/main/assets' / ('LegacyTVHub_' + product['version'] + '.zip')
with ZipFile(package) as z:
    assert {'config.xml', 'index.html', 'main.js', 'style.css', 'images/thumb.png', 'images/bigthumb.png'} <= set(z.namelist())
    xml = ET.fromstring(z.read('config.xml'))
    assert xml.findtext('widgetname') == 'Legacy TV Hub'
    assert xml.findtext('ver') == product['version']
    assert z.read('images/thumb.png').startswith(b'\x89PNG')
print('Contract OK:', product['version'], product['androidApplicationId'], product['orsayWidgetId'])
