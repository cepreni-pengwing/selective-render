import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('beam_catalog',Path(__file__).with_name('generate-beam-catalog.py'))
catalog=importlib.util.module_from_spec(spec);spec.loader.exec_module(catalog)

class BeamCatalogTest(unittest.TestCase):
    def test_family_expansion_preserves_small_branches_but_rejects_volume_shapes_and_full_models(self):
        with tempfile.TemporaryDirectory() as directory:
            jar=Path(directory)/'fixture.jar'
            with zipfile.ZipFile(jar,'w') as archive:
                def model(name,data):archive.writestr('assets/conquest/models/block/'+name+'.json',json.dumps(data))
                def state(name,modelname):archive.writestr('assets/conquest/blockstates/'+name+'.json',json.dumps({'variants':{'':{'model':'conquest:block/'+modelname}}}))
                model('slender',{'elements':[{'from':[4,0,4],'to':[12,16,12]}]})
                model('full',{'elements':[{'from':[0,0,0],'to':[16,16,16]}]})
                model('inherited',{'parent':'conquest:block/full'})
                for name in ['oak_log_small_branch','other_species_branch','new_material_fence_gate','heavy_wooden_door']:
                    state(name,'slender')
                for name in ['oak_beam_stairs','oak_beam_vertical_slab','oak_beam_quarter','oak_branch_layer','oak_beam_smallblock']:
                    state(name,'slender')
                state('brick_wall','inherited');state('unrelated_solid_block','slender')
            included,excluded=catalog.generate({'conquest':jar})
            self.assertEqual(set(included),{'conquest:'+n for n in ['oak_log_small_branch','other_species_branch','new_material_fence_gate','heavy_wooden_door']})
            self.assertIn('conquest:brick_wall',excluded)
            self.assertNotIn('conquest:unrelated_solid_block',included)

    def test_oversized_full_cube_and_vanilla_cube_parents_are_also_rejected(self):
        self.assertTrue(catalog.full_box({'from':[-1,-1,-1],'to':[17,17,17]}))
        self.assertFalse(catalog.full_box({'from':[0,0,0],'to':[16,8,16]}))
        with tempfile.TemporaryDirectory() as directory:
            jar=Path(directory)/'fixture.jar'
            with zipfile.ZipFile(jar,'w') as archive:
                archive.writestr('assets/conquest/models/block/solid.json',json.dumps({'parent':'minecraft:block/cube_all'}))
                archive.writestr('assets/conquest/blockstates/solid_beam.json',json.dumps({'variants':{'':{'model':'conquest:block/solid'}}}))
            included,_=catalog.generate({'conquest':jar});self.assertEqual(included,[])

if __name__=='__main__':unittest.main()
