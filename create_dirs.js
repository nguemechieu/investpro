const fs = require('fs');
const path = require('path');

const baseDir = 'C:\\Users\\nguem\\Documents\\GitHub\\investpro';
const dirs = [
  'src\\main\\java\\org\\investpro\\exchange\\registry',
  'src\\main\\java\\org\\investpro\\exchange\\health',
  'src\\main\\java\\org\\investpro\\exchange\\normalization'
];

dirs.forEach(dir => {
  const fullPath = path.join(baseDir, dir);
  fs.mkdirSync(fullPath, { recursive: true });
  console.log(`Created: ${fullPath}`);
});

console.log('All directories created successfully');
