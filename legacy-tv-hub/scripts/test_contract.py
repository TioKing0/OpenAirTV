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
assert product['androidVersionCode'] >= 2
assets = root / 'app/src/main/assets'
version = product['version']
network = assets / ('LegacyTVHub_' + version + '.zip')
usb = assets / ('LegacyTVHub_USB_' + version + '.zip')
expected = {'config.xml', 'index.html', 'main.js', 'style.css', 'images/thumb.png', 'images/bigthumb.png'}
with ZipFile(network) as z:
    assert expected <= set(z.namelist())
    cfg = ET.fromstring(z.read('config.xml'))
    assert cfg.findtext('widgetname') == 'Legacy TV Hub'
    assert cfg.findtext('ver') == version
    assert z.read('images/thumb.png').startswith(b'\x89PNG')
with ZipFile(usb) as z:
    assert {'LegacyTVHub/' + p for p in expected} <= set(z.namelist())
    assert ET.fromstring(z.read('LegacyTVHub/config.xml')).findtext('ver') == version
server = (root/'app/src/main/java/br/com/legacytvhub/manager/LocalWidgetServer.java').read_text()
activity = (root/'app/src/main/java/br/com/legacytvhub/manager/MainActivity.java').read_text()
assert 'new ServerSocket(8080' in server
assert 'LegacyTVHub_USB_' in activity
assert 'ACTION_CREATE_DOCUMENT' in activity
print('Contract OK:', version, product['androidApplicationId'], product['orsayWidgetId'], 'USB and LAN')
