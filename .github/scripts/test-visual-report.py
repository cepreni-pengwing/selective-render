import importlib.util
from pathlib import Path
import unittest
from PIL import Image

path=Path(__file__).with_name('visual-report.py')
spec=importlib.util.spec_from_file_location('visual_report',path)
report=importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)

class VisualReportTest(unittest.TestCase):
    def fixtures(self):
        colors={
            '01-normal':(100,100,100),'02-color-0':(100,100,100),
            '03-color-25':(139,75,75),'04-color-50':(178,50,50),
            '05-color-75':(216,25,25),'06-color-100':(255,0,0),
            '07-texture':(80,180,180),'08-texture-color':(168,90,90),
            '10-culled':(80,150,230),'11-only-stone':(100,100,100),
        }
        images={key:Image.new('RGB',(120,80),value) for key,value in colors.items()}
        rainbow=Image.new('RGB',(120,80))
        for x in range(120):
            rgb=tuple(round(c*255) for c in report.colorsys.hsv_to_rgb(x/120,1,1))
            for y in range(80): rainbow.putpixel((x,y),rgb)
        images['09-rainbow']=rainbow
        images['06-color-100'].paste((50,100,30),(30,12,90,20))
        split=Image.new('RGB',(120,80),(255,0,0))
        split.paste((0,0,255),(0,0,60,80))
        images['12-region-split']=split
        return images

    def failed(self,images):
        return [check['name'] for check in report.check_world(images) if not check['passed']]

    def test_correct_blending_and_regions_pass(self):
        self.assertEqual([],self.failed(self.fixtures()))

    def test_cold_biome_blue_green_grass_is_not_a_false_failure(self):
        images=self.fixtures()
        images['06-color-100'].paste((66,93,82),(30,12,90,20))
        self.assertEqual([],self.failed(images))

    def test_opacity_collapsed_to_opaque_is_detected(self):
        images=self.fixtures()
        for key in ['03-color-25','04-color-50','05-color-75']:
            images[key]=images['06-color-100'].copy()
        self.assertIn('Opacity changes the rendered image at every quarter',self.failed(images))

    def test_uniform_rainbow_is_detected(self):
        images=self.fixtures(); images['09-rainbow']=images['06-color-100'].copy()
        self.assertIn('Rainbow renders several hues',self.failed(images))

    def test_retextured_grass_is_detected(self):
        images=self.fixtures(); images['06-color-100']=Image.new('RGB',(120,80),(255,0,0))
        self.assertIn('Grass decoration keeps its native material',self.failed(images))

    def test_native_grass_overlay_on_opaque_boundary_is_detected(self):
        images=self.fixtures()
        images['06-color-100'].paste((50,100,30),(44,29,72,34))
        self.assertIn('Opaque Color covers coplanar grass side overlays',self.failed(images))

    def test_saved_grid_text_cannot_become_transparent(self):
        image=Image.new('RGB',(1280,720),(0,0,0))
        self.assertFalse(report.grid_text_visible(image))
        image.paste((255,255,255),(610,180,625,188))
        self.assertTrue(report.grid_text_visible(image))

    def test_region_color_leak_is_detected(self):
        images=self.fixtures(); images['12-region-split']=images['06-color-100'].copy()
        self.assertIn('Regional blue and global red stay independent',self.failed(images))

if __name__=='__main__': unittest.main()
