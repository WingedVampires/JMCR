/*
  Copyright (c) 2011,2012, 
   Saswat Anand (saswat@gatech.edu)
   Mayur Naik  (naik@cc.gatech.edu)
  All rights reserved.
  
  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met: 
  
  1. Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer. 
  2. Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution. 
  
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  
  The views and conclusions contained in the software and documentation are those
  of the authors and should not be interpreted as representing official policies, 
  either expressed or implied, of the FreeBSD Project.
*/


package edu.tamu.aser.constraints;

import edu.tamu.aser.accelerate.MatchUnsatModel;
import edu.tamu.aser.config.Configuration;
import org.w3c.tools.sexpr.SimpleSExprStream;
import org.w3c.tools.sexpr.Symbol;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Vector;

/**
 * Parser of the model returned by Z3
 *
 * @author jeffhuang
 */

public class GetModel {
	public static Model read(File file) {
		try {
            MatchUnsatModel.getInstance().setStandradTime(System.currentTimeMillis() - MatchUnsatModel.getInstance().errorStTime);
            FileInputStream fis = new FileInputStream(file);
            SimpleSExprStream p = new SimpleSExprStream(fis);
            p.setListsAsVectors(true);

            String result = readResult(p);

            //get sovling time
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            if (result.startsWith("(error "))
				throw new Error("smt file has errors");
			//System.out.println("Feasible: " + "sat".equals(result));

			if ("sat".equals(result)) {
				//if the constraints are satisfied

				Model model = process((Vector) p.parse());

				String line = reader.readLine();
				while (line != null) {
					if (line.contains(":time")) {
//						String[] vStrings = line.split("\\s+");
						String time = line.split("\\s+")[2];

						float f = Float.valueOf(time.replace(")", ""));
						float t = f * 1000;
						Configuration.solveTime += t;
						break;
					}
					line = reader.readLine();
				}

				fis.close();
				return model;
			} else if ("unsat".equals(result)) {
                // 设置匹配的停止时间
//				MatchUnsatModel.getInstance().setStandradTime(System.currentTimeMillis() - MatchUnsatModel.getInstance().errorStTime);
                //constraint not satisfied
				String line = reader.readLine();
				while (line != null) {
					if (line.contains("A")) {
						MatchUnsatModel.getInstance().addUnsatCore(line.substring(1, line.length() - 1));
					}

					if (line.contains(":time")) {
//						String[] vStrings = line.split("\\s+");
						String time = line.split("\\s+")[2];
						float f = Float.valueOf(time.replace(")", ""));
						float t = f * 1000;
						Configuration.solveTime += t;
						break;
					}
					line = reader.readLine();
				}
			} else
				System.err.println("Solver error: " + result);

			fis.close();
			return null;
		} catch (Exception | Error e) {
			//throw new Error(e);
			e.printStackTrace();
			return null;
		}
	}

	static String readResult(SimpleSExprStream p) throws IOException {
		final int len = 10;
		char[] cs = new char[len];
		int c = p.read();

		int i = 0;
		while (c > 0 && c != '\n' && c != '\r') {
			if (i < len)
				cs[i++] = (char) c;
			c = p.read();
		}
		if (c < 0)
			return null;
		return new String(cs, 0, i);
	}

	private static Model process(Vector root) {

		if (!((Symbol) root.elementAt(0)).toString().equals("model"))
			assert false;

		Model model = new Model();

		int size = root.size();
		for (int i = 1; i < size; i++) {
			// 当检测到返回Ax的Bool值时停止扫描
			if (((Vector) root.elementAt(i)).elementAt(1).toString().contains("A"))
				break;

			define_fun(root.elementAt(i), model);
		}

		return model;
	}

	static private void define_fun(Object obj, Model model) {
		Vector node = (Vector) obj;
		if (!((Symbol) node.elementAt(0)).toString().equals("define-fun"))
			assert false;
		String varName = ((Symbol) node.elementAt(1)).toString();
		//System.out.print(varName + " ");

		Object value = value(node.elementAt(2), node.elementAt(3), node.elementAt(4));
		//System.out.println(value);

		model.put(varName, value);
	}

	static private Object value(Object paramTypes, Object type, Object v) {
		if (type instanceof Symbol) {
			String t = ((Symbol) type).toString();
			return primValue(paramTypes, t, v);
		} else if (type instanceof Vector) {
			Object t = ((Vector) type).elementAt(0);
			if (t instanceof Symbol) {
				if (((Symbol) t).toString().equals("Array")) {
					Vector value = (Vector) v;
					if (!("_".equals(((Symbol) value.elementAt(0)).toString())))
						assert false;
					if (!("as-array".equals(((Symbol) value.elementAt(1)).toString())))
						assert false;
					return ((Symbol) value.elementAt(2)).toString();
				} else
					assert false;
			} else
				assert false;
		}
		return null;
	}

	static private Object primValue(Object paramTypes, String t, Object v) {
		if ("Int".equals(t) || "Real".equals(t)) {
			Number ret = number(t, v);
			if (ret != null) {
				return ret;
			} else {
				Vector value = (Vector) v;
				String opName = ((Symbol) value.elementAt(0)).toString();
				if (opName.equals("ite")) {
					Model.Array array = new Model.Array();
					ite(t, value, array);
					return array;
				} else
					assert false;
			}
		} else
			assert false;
		return null;
	}

	static private void ite(String t, Vector iteExpr, Model.Array array) {
		String opName = ((Symbol) iteExpr.elementAt(0)).toString();
		if (opName.equals("ite")) {
			Vector cond = (Vector) iteExpr.elementAt(1);
			Object thenVal = iteExpr.elementAt(2);
			Object elseVal = iteExpr.elementAt(3);

			//find the index
			Integer index = null;
			if (((Symbol) cond.elementAt(0)).toString().equals("=")) {
				index = (Integer) number("Int", cond.elementAt(2));
			} else
				assert false;

			array.put(index, number(t, thenVal));

			Number num = number(t, elseVal);
			if (num != null) {
				array.setDefaultValue(num);
			} else {
				//must be another ite
				ite(t, (Vector) elseVal, array);
			}
		}
	}

	static private Number number(String t, Object v) {
		if (v instanceof Number) {
			//positive number
			return (Number) v;
		} else if (v instanceof Vector) {
			Vector value = (Vector) v;
			String opName = ((Symbol) value.elementAt(0)).toString();
			if (opName.equals("-")) {
				//negative number
				v = value.elementAt(1);
				if (v instanceof Vector) {
					//negative rational number
					value = (Vector) v;
					opName = ((Symbol) value.elementAt(0)).toString();
					if (opName.equals("/")) {
						Number numerator = (Number) value.elementAt(1);
						Number denominator = (Number) value.elementAt(2);
						return fromRational(false, numerator, denominator);
					} else
						assert false;
				} else {
					Number mag = (Number) v;
					//System.out.println("**"+type);
					if ("Int".equals(t)) {
						return 0 - mag.intValue();
					} else if ("Real".equals(t)) {
						return 0 - mag.doubleValue();
					} else
						assert false;
					//System.out.println(v);
				}
			} else if (opName.equals("/")) {
				Number numerator = (Number) value.elementAt(1);
				Number denominator = (Number) value.elementAt(2);
				return fromRational(true, numerator, denominator);
			}
			return null;
		}
		throw new RuntimeException("");
	}

	private static Double fromRational(boolean sign, Number numerator, Number denominator) {
		DecimalFormat oneDForm = new DecimalFormat("#.#");
		double d = numerator.doubleValue() / denominator.doubleValue();
		d = Double.valueOf(oneDForm.format(d));
		d = sign ? d : -d;
		return d;
	}

	//for testing
	public static void main(String[] args) {
		String dirName = "z3";
		File dir = new File(dirName);
		if (!dir.exists())
			throw new RuntimeException();

		File[] z3OutFiles;
		if (args.length > 1) {
			z3OutFiles = new File[1];
			z3OutFiles[0] = new File(dir, "z3out." + 1);
		} else {
			z3OutFiles = dir.listFiles(new java.io.FileFilter() {
				public boolean accept(File f) {
					return f.getName().startsWith("z3out.");
				}
			});
		}

		assert z3OutFiles != null;
		for (File f : z3OutFiles) {
			System.out.println("reading " + f.getName());
			read(f);
		}
	}
}