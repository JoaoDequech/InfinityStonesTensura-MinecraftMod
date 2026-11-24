// Run inside Blockbench (risky_eval). Builds a Modded Entity project from build/<model>.json and a texture.
(function () {
    const fs = require('fs');
    const HL = {};
    window.HL = HL;
    const ROOT = 'D:/InfinityTensura/oc-models/tools/blockbench/';

    HL.load = function (model, style) {
        const d = JSON.parse(fs.readFileSync(ROOT + 'build/' + model + '.json', 'utf8'));
        Project.texture_width = d.tex_w;
        Project.texture_height = d.tex_h;
        Undo.initEdit({ outliner: true, textures: [] });
        const build = (gd, parent) => {
            const g = new Group({ name: gd.name, origin: gd.origin, rotation: gd.rot });
            g.rest = gd.rot;
            if (parent) g.addTo(parent);
            g.init();
            for (const c of gd.cubes) {
                new Cube({ name: c.name, from: c.from, to: c.to, origin: gd.origin, box_uv: true, uv_offset: c.uv }).addTo(g).init();
            }
            for (const k of gd.kids) build(k, g);
        };
        for (const g of d.groups) build(g, null);
        const tex = new Texture({ name: style + '.png' }).fromPath(ROOT + 'textures/' + style + '.png').add(false);
        HL.tex = tex;
        Undo.finishEdit('load');
        return tex;
    };

    HL.apply = function () {
        Cube.all.forEach(c => c.applyTexture(HL.tex, true));
        Canvas.updateAll();
    };

    HL.group = (n) => Group.all.find(g => g.name === n);

    HL.export = function (model) {
        HL.pose(0, 0);
        fs.writeFileSync(ROOT + 'java/_raw_' + model + '.java', Codecs.modded_entity.compile());
        fs.writeFileSync(ROOT + model + '.bbmodel', Codecs.project.compile());
        return 'exported ' + model;
    };

    // camera presets: [position, target]
    HL.shot = function (file, pos, target) {
        const p = Preview.selected;
        p.camera.position.set(pos[0], pos[1], pos[2]);
        p.controls.target.set(target[0], target[1], target[2]);
        p.controls.update && p.controls.update();
        p.render();
        const url = p.canvas.toDataURL('image/png');
        fs.writeFileSync(ROOT + 'renders/' + file, Buffer.from(url.split(',')[1], 'base64'));
        return file;
    };

    HL.clear = function () {
        Project.texture_width = 16;
        Project.texture_height = 16;
        Undo.initEdit({ outliner: true });
        [...Group.all].reverse().forEach(g => g.remove(false));
        Cube.all.slice().forEach(c => c.remove(false));
        Texture.all.slice().forEach(t => t.remove(false));
        Undo.finishEdit('clear');
    };

    // same maths as the Java side (angles in degrees, Blockbench sign convention)
    HL.pose = function (curl, spread, thumbSpread) {
        const e = (t) => t * t * (3 - 2 * t);
        for (let i = 0; i < 4; i++) {
            const lag = [0.12, 0.07, 0.03, 0][i];
            const c = e(Math.max(0, Math.min(1, (curl - lag) / (1 - lag))));
            const f = [HL.group('finger' + i), HL.group('finger' + i + '_mid'), HL.group('finger' + i + '_tip')];
            f[0].rotation = [-c * 90, 0, (i - 1.5) * (spread || 0) * 7];
            f[1].rotation = [-c * 98, 0, 0];
            f[2].rotation = [-c * 82, 0, 0];
        }
        const t = HL.group('thumb'), tt = HL.group('thumb_tip');
        t.rotation = [-curl * 62, 0, 28 + (spread || 0) * 14 - curl * 10];
        tt.rotation = [-curl * 70, 0, -curl * 25];
        Canvas.updateAllBones();
    };

    return 'ok';
})();
