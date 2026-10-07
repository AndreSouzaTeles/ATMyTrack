"""Copy the existing approved artwork and payment resources; no new identity."""
from pathlib import Path
import json
import shutil
from PIL import Image

root = Path(__file__).resolve().parents[2]
assets = root / 'ios/Resources/Assets.xcassets'
assets.mkdir(parents=True, exist_ok=True)
(assets/'Contents.json').write_text(json.dumps({'info':{'version':1,'author':'xcode'}}))
for name, source in [('Brand','brand_art.png'),('SupportPix','support_pix.jpg')]:
    folder=assets/(name+'.imageset');folder.mkdir(exist_ok=True)
    shutil.copy2(root/'app/src/main/res/drawable-nodpi'/source,folder/source)
    (folder/'Contents.json').write_text(json.dumps({'images':[{'idiom':'universal','filename':source}],'info':{'version':1,'author':'xcode'}}))
folder=assets/'AppIcon.appiconset';folder.mkdir(exist_ok=True)
original=Image.open(root/'app/src/main/res/drawable-nodpi/brand_art.png').convert('RGBA')
background=Image.new('RGB',original.size,(9,11,17));background.paste(original,mask=original.getchannel('A'))
background.resize((1024,1024),Image.Resampling.LANCZOS).save(folder/'AppIcon.png')
(folder/'Contents.json').write_text(json.dumps({'images':[{'idiom':'universal','platform':'ios','size':'1024x1024','filename':'AppIcon.png'}],'info':{'version':1,'author':'xcode'}}))
folder=assets/'LaunchBackground.colorset';folder.mkdir(exist_ok=True)
(folder/'Contents.json').write_text(json.dumps({'colors':[{'idiom':'universal','color':{'color-space':'srgb','components':{'red':'0.035','green':'0.043','blue':'0.065','alpha':'1'}}}],'info':{'version':1,'author':'xcode'}}))
shutil.copy2(root/'app/src/main/assets/supporters.json',root/'ios/Resources/supporters.json')
shutil.copytree(root/'app/src/main/assets/licenses',root/'ios/Resources/licenses',dirs_exist_ok=True)
