/*
 * $Id$
 */

package ru.ifmo.cs.bcomp;

import static ru.ifmo.cs.bcomp.CS.*;
import static ru.ifmo.cs.bcomp.State.*;
import static ru.ifmo.cs.bcomp.Utils.cs;

/**
 *
 * @author Dmitry Afanasiev <KOT@MATPOCKuH.Ru>
 */
public class MicroCode {
	private class omc {
		public final String label;
		private final long microcmd;
		private final CS[] signals;

		public omc(String label, CS[] signals) {
			long microcmd = 0L;

			this.label = label;

			for (CS cs : (this.signals = signals)) {
				microcmd |= 1L << cs.ordinal();
			}

			this.microcmd = microcmd;
		}

		public omc(CS[] signals) {
			this(null, signals);
		}

		public long getMicroCommand() throws Exception {
			if (label != null)
				for (omc mc : MP)
					if (this != mc)
						if (label.equals(mc.label))
							throw new Exception("Found duplicate label '" + label + "'");

			return microcmd;
		}
	}

	private class CMC extends omc {
		private final String labelto;
		private final long microcmd;

		public CMC(String label, CS[] signals, long startbit, long expected, String labelto) {
			super(label, signals);

			this.labelto = labelto;
			microcmd = (1L << TYPE.ordinal()) + (1L << (startbit + 16)) + (expected << 32);
		}

		public CMC(CS[] signals, long startbit, long expected, String labelto) {
			this(null, signals, startbit, expected, labelto);
		}

		@Override
		public long getMicroCommand() throws Exception {
			return microcmd | super.getMicroCommand() | (((long)findLabel(labelto)) << 24);
		}
	}

	private final omc[] MP = {
		// Пустая ячейка
		new omc(			cs()),

		// Выборка команды
		new omc("BEGIN",	cs(RDIP, HTOH, LTOL, WRAR, WRBR)),							// IP -> AR, BR
		new omc(			cs(RDBR, PLS1, HTOH, LTOL, WRIP, LOAD)),						// BR + 1 -> IP, MEM(AR) -> DR
		new omc(			cs(RDDR, HTOH, LTOL, WRCR)),									// DR -> CR
		// Частичное декодирование
		new CMC(			cs(RDCR, HTOL), 7, 1,							"CHKBR"),	// if CR(15) = 1 then GOTO CHKBR
		new CMC(			cs(RDCR, HTOL), 6, 1,							"ADDRTYPE"),// if CR(14) = 1 then GOTO ADDRTYPE
		new CMC(			cs(RDCR, HTOL), 5, 1,							"ADDRTYPE"),// if CR(13) = 1 then GOTO ADDRTYPE
		new CMC(			cs(RDCR, HTOL), 4, 0,							"ADDRLESS"),// if CR(12) = 0 then GOTO ADDRLESS
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"IO"),		// GOTO IO
		new CMC("CHKBR",	cs(RDCR, HTOL), 6, 0,							"ADDRTYPE"),// if CR(14) = 0 then GOTO ADDRTYPE
		new CMC(			cs(RDCR, HTOL), 5, 0,							"ADDRTYPE"),// if CR(13) = 0 then GOTO ADDRTYPE
		new CMC(			cs(RDCR, HTOL), 4, 1,							"BRANCHES"),// if CR(12) = 1 then GOTO BRANCHES

		// Выборка адреса
		new CMC("ADDRTYPE",	cs(RDCR, HTOL), 3, 0,							"LOADOPER"),// if CR(11) = 0 then GOTO LOADOPER
		new omc("T1XXX",	cs(RDCR, SEXT, WRBR)),										// LTOL(CR) -> BR
		new CMC(			cs(RDCR, HTOL), 2, 1,							"T11XX"),	// if CR(10) = 1 then GOTO T11XX
		new omc("T10XX",	cs(RDBR, RDIP, HTOH, LTOL, WRAR)),							// CR -> AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR 
		new CMC(			cs(RDCR, HTOL), 1, 1,							"T101X"),	// if CR(9) = 1 then GOTO T101X
		new	CMC("T100X",	cs(RDCR, HTOL), 0, 1,							"RESERVED"),// if CR(8) = 1 then GOTO RESERVED
		new CMC("T1000",	cs(RDPS, LTOL), PS0.ordinal(), 0,				"LOADOPER"),// GOTO LOADOPER
		new CMC("T101X",	cs(RDCR, HTOL), 0, 1,							"T1011"),	// if CR(8) = 1 then GOTO T1011
		new omc("T1010",	cs(RDDR, PLS1, HTOH, LTOL, WRDR)),							// DR + 1 -> DR
		new omc(			cs(STOR)),													// DR -> MEM(AR)
		new omc(			cs(RDDR, COML, HTOH, LTOL, WRDR)),							// DR - 1 -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"LOADOPER"),// GOTO LOADOPER
		new omc("T1011",	cs(RDDR, COML, HTOH, LTOL, WRDR)),							// DR - 1 -> DR
		new omc(			cs(STOR)),													// DR -> MEM(AR)
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"LOADOPER"),// GOTO LOADOPER
		new CMC("T11XX",	cs(RDCR, HTOL), 1, 1,							"T111X"),	// if CR(9) = 1 then GOTO T111X
		new CMC("T110X",	cs(RDCR, HTOL), 0, 1,							"RESERVED"),// if CR(8) = 1 then GOTO T1101
		new omc("T1100",	cs(RDBR, RDSP, HTOH, LTOL, WRDR)),							// BR + SP -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"LOADOPER"),// GOTO LOADOPER
		new CMC("T111X",	cs(RDCR, HTOL), 0, 0,							"T1110"),	// if CR(8) = 0 then GOTO RESERVED
		new omc("T1111",	cs(RDBR, HTOH, LTOL, WRDR)),									// BR -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"EXECUTE"),	// GOTO EXECUTE
		new omc("T1110",	cs(RDBR, RDSP, HTOH, LTOL, WRDR)),							// BR + SP -> DR

		// Выборка операнда
		new CMC("LOADOPER",	cs(RDCR, HTOL), 7, 0,							"RDVALUE"),	// if CR(15) = 0 then GOTO RDVALUE
		new CMC(			cs(RDCR, HTOL), 6, 1,							"CMD11XX"),	// if CR(14) = 1 then GOTO CMD11XX
		new omc("RDVALUE",	cs(RDDR, HTOH, LTOL, WRAR)),									// DR -> AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR

		// Декодирование и цикл исполнения адресных команд кроме JUMP/CALL/ST/FXXX
		new CMC("EXECUTE",	cs(RDCR, HTOL), 7, 1,							"CMD1XXX"),	// if CR(15) = 1 then GOTO CMD1XXX
		new CMC("CMD0XXX",	cs(RDCR, HTOL), 6, 1,							"CMD01XX"),	// if CR(14) = 1 then GOTO CMD01XX
		// 13th bit already checked !!! CHECK LABEL NAME !!!
		new CMC("CMD000X",	cs(RDCR, HTOL), 4, 1,							"OR"),		// if CR(12) = 1 then GOTO OR
		new omc("AND",		cs(RDAC, RDDR, SORA, HTOH, LTOL, STNZ, WRAC)),				// AC & DR -> AC, N, Z
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("OR",		cs(RDAC, RDDR, COML, COMR, SORA, HTOH, LTOL, WRBR)),			// ~AC & ~DR -> BR
		new omc(			cs(RDBR, COML, HTOH, LTOL, STNZ, WRAC)),						// ~BR -> AC, N, Z
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("CMD01XX",	cs(RDCR, HTOL), 5, 1,							"CMD011X"),	// if CR(13) = 1 then GOTO CMD011X
		new CMC("CMD010X",	cs(RDCR, HTOL), 4, 1,							"ADC"),		// if CR(12) = 1 then GOTO ADC
		new omc("ADD",		cs(RDAC, RDDR, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),			// AC + DR -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("ADC",		cs(RDPS, LTOL), C.ordinal(), 0,					"ADD"),		// if C = 0 then GOTO ADD
		new omc(			cs(RDAC, RDDR, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),	// DR + AC + 1 -> BR, C, N, Z, V
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("CMD011X",	cs(RDCR, HTOL), 4, 1,							"CMP"),		// if CR(12) = 1 then GOTO CMP
		new omc("SUB",		cs(RDAC, RDDR, COMR, PLS1, STNZ, SETV, SETC, WRAC)),			// ~DR + AC + 1 -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("CMP",		cs(RDAC, RDDR, COMR, PLS1, STNZ, SETV, SETC)),				// ~DR + AC + 1 -> N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		// Warning - 11XX was already checked
		new CMC("CMD1XXX",	cs(RDCR, HTOL), 5, 1,							"CMD101X"),	// if CR(13) = 1 then GOTO CMD101X
		new CMC("CMD100X",	cs(RDCR, HTOL), 4, 1,							"RESERVED"),// if CR(12) = 1 then GOTO RESERVED
		new omc("LOOP",		cs(RDDR, COML, HTOH, LTOL, WRDR)),							// DR + ~0 -> DR
		new omc(			cs(STOR)),													// DR -> MEM(AR)
		new CMC(			cs(RDDR, HTOL), 7, 1,							"INT"),		// if DR(15) = 1 then GOTO INT
		new omc(			cs(RDIP, PLS1, WRIP)),										// IP + 1 -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("CMD101X",	cs(RDCR, HTOL), 4, 1,							"SWAM"),	// if CR(12) = 1 then GOTO SWAM
		// !!! CHECK FLAGS !!!
		new omc("LD",		cs(RDDR, HTOH, LTOL, WRAC)),									// DR -> AC
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("SWAM",		cs(RDDR, HTOL, LTOL, WRBR)),									// DR -> BR
		new omc(			cs(RDAC, HTOH, LTOL, WRDR)),									// AC -> DR
		// !!! CHECK FLAGS !!!
		new omc(			cs(RDBR, HTOH, LTOL, WRAC, STOR)),							// DR -> MEM(AR), BR -> AC
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		// Warning - 1111 was already checked (addressless command)
		new CMC("CMD11XX",	cs(RDCR, HTOL), 5, 1,							"ST"),		// if CR(13) = 1 then GOTO ST
		new CMC("CMD110X",	cs(RDCR, HTOL), 4, 1,							"CALL"),	// if CR(12) = 1 then GOTO CALL
		new omc("JUMP",		cs(RDDR, HTOH, LTOL, WRIP)),									// DR -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("CALL",		cs(RDDR, HTOH, LTOL, WRBR)),									// DR -> BR
		new omc(			cs(RDIP, HTOH, LTOL, WRDR)),									// IP -> DR
		new omc(			cs(RDBR, HTOH, LTOL, WRIP)),									// BR -> IP
		new omc("PUSHVAL",	cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),						// SP - 1 -> SP, AR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"STORE"),	// GOTO STORE
		new omc("ST",		cs(RDDR, HTOH, LTOL, WRAR)),									// DR -> AR
		new omc(			cs(RDAC, HTOH, LTOL, WRDR)),									// AC -> DR
		new omc("STORE",	cs(STOR)),													// DR -> MEM(AR)
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		// Команды с "коротким" переходом
		new CMC("BRANCHES",	cs(RDCR, HTOL), 3, 1,							"BR1XXX"),	// if CR(11) = 1 then GOTO BR1XXX
		new CMC("BR0XXX",	cs(RDCR, HTOL), 2, 1,							"BR01XX"),	// if CR(10) = 1 then GOTO BR01XX
		new CMC("BR00XX",	cs(RDCR, HTOL), 1, 1,							"BR001X"),	// if CR(9) = 1 then GOTO BR001X
		new CMC("BR000X",	cs(RDCR, HTOL), 0, 1,							"BNE"),		// if CR(8) = 1 then GOTO BNE
		new CMC("BEQ",		cs(RDPS, LTOL), Z.ordinal(), 0,					"INT"),		// if Z = 0 then GOTO INT
		// !!! Значение какого регистра используем? DR или CR?
		new omc("BR",		cs(RDCR, SEXT, WRBR)),										// SEXT(CR) -> BR
		new omc(			cs(RDBR, RDIP, HTOH, LTOL, WRIP)),							// BR + IP -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BNE",		cs(RDPS, LTOL), Z.ordinal(), 0,					"BR"),		// if Z = 0 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BR001X",	cs(RDCR, HTOL), 0, 1,							"BPL"),		// if CR(8) then GOTO BPL
		new CMC("BMI",		cs(RDPS, LTOL), N.ordinal(), 0,					"BR"),		// if N = 0 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BPL",		cs(RDPS, LTOL), N.ordinal(), 1,					"BR"),		// if N = 1 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BR01XX",	cs(RDCR, HTOL), 1, 1,							"BR011X"),	// if CR(9) = 1 then GOTO BR011X
		new CMC("BR010X",	cs(RDCR, HTOL), 0, 1,							"BCC"),		// if CR(8) = 1 then GOTO BCC
		new CMC("BCS",		cs(RDPS, LTOL), C.ordinal(), 0,					"BR"),		// if C = 0 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BCC",		cs(RDPS, LTOL), C.ordinal(), 1,					"BR"),		// if C = 1 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BR011X",	cs(RDCR, HTOL), 0, 1,							"BVC"),		// if CR(8) = 1 then GOTO BCC
		new CMC("BVS",		cs(RDPS, LTOL), V.ordinal(), 0,					"BR"),		// if V = 0 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BVC",		cs(RDPS, LTOL), V.ordinal(), 1,					"BR"),		// if V = 1 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BR1XXX",	cs(RDCR, HTOL), 2, 1,							"RESERVED"),// if CR(10) = 1 then GOTO RESERVED
		new CMC("BR10XX",	cs(RDCR, HTOL), 1, 1,							"RESERVED"),// if CR(9) = 1 then GOTO RESERVED
		new CMC("BR100X",	cs(RDCR, HTOL), 0, 1,							"BFC"),		// if CR(8) = 1 then GOTO BFC
		new CMC("BFS",		cs(RDPS, LTOL), F.ordinal(), 0,					"BR"),		// if F = 0 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("BFC",		cs(RDPS, LTOL), F.ordinal(), 1,					"BR"),		// if F = 1 then GOTO BR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT

		// Безадресные команды
		new CMC("ADDRLESS",	cs(RDCR, HTOL), 3, 1,							"AL1XXX"),	// if CR(11) = 1 then GOTO AL1XXX
		new CMC("AL0XXX",	cs(RDCR, HTOL), 2, 1,							"AL01XX"),	// if CR(10) = 1 then GOTO AL01XX
		new CMC("AL00XX",	cs(RDCR, HTOL), 1, 1,							"AL001X"),	// if CR(9) = 1 then GOTO AL001X
		new CMC("AL000X",	cs(RDCR, HTOL), 0, 0,							"INT"),		// if CR(8) = 0 then GOTO INT (NOP)
		new omc("HLT",		cs(HALT)),													// HLT: HALT
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"BEGIN"),	// GOTO BEGIN
		new CMC("AL001X",	cs(RDCR, HTOL), 0, 1,							"AL0011"),	// if CR(8) = 1 then GOTO AL0011
		new CMC("AL0010",	cs(RDCR, LTOL), 7, 1,							"CMA"),		// if CR(7) = 1 then GOTO CMA
		new omc("CLA",		cs(STNZ, SETV, WRAC)),										// 0 -> AC, N, V, Z
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("CMA",		cs(RDAC, COML, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),			// ~AC + 0 -> BR, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("AL0011",	cs(RDCR, LTOL), 7, 1,							"CMC"),		// if (CR7) = 1 then GOTO CMC
		new omc("CLC",		cs(SETC)),													// 0 -> C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("CMC",		cs(RDPS, LTOL), C.ordinal(), 1,					"CLC"),		// if C = 1 then GOTO CLC
		new omc(			cs(COML, COMR, HTOH, SETC)),									// 1 -> C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("AL01XX",	cs(RDCR, HTOL), 1, 1,							"AL011X"),	// if CR(9) = 1 then GOTO AL011X
		new CMC("AL010X",	cs(RDCR, HTOL), 0, 1,							"AL0101"),	// if CR(8) = 1 then GOTO AL0101
		new CMC("AL0100",	cs(RDCR, LTOL), 7, 1,							"ROR"),		// if CR(7) = 1 then GOTO ROR
		new omc("ROL",		cs(RDAC, SHLT, SHL0, STNZ, SETC, WRAC)),					// ROL(AC) -> AC, C, N, Z
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		// !!! CHECK: may be need move SHRF to ASR command
		new omc("ROR",		cs(RDAC, SHRT, SHRF, STNZ, SETC, WRAC)),					// ROR(AC) -> AC, C, N, Z
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("AL0101",	cs(RDCR, LTOL), 7, 1,							"ASR"),		// if CR(7) = 1 then GOTO ASR
		new omc("ASL",		cs(RDAC, HTOH, LTOL, WRBR)),									// AC -> BR
		new omc(			cs(RDAC, RDBR, STNZ, SETV, SETC, WRAC)),						// AC + BR -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("ASR",		cs(RDAC, SHRT, STNZ, SETV, SETC, WRAC)),					// ASR(AC) -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("AL011X",	cs(RDCR, HTOL), 0, 1,							"AL0111"),	// if CR(8) = 1 then GOTO AL0111
		new CMC("AL0110",	cs(RDCR, LTOL), 7, 1,							"SWAB"),	// if CR(7) = 1 then GOTO SWAB
		new CMC("AL01100",	cs(RDCR, LTOL), 6, 1,							"TST"),		// if CR(6) = 1 then GOTO TST
		new omc("SXTB",		cs(RDAC, SEXT, STNZ, SETV, WRAC)),							// SEXT(AC) -> AC, N, Z, V
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("TST",		cs(RDAC, HTOH, LTOL, STNZ, SETV)),							// AC -> N, Z, V
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("SWAB",		cs(RDAC, HTOL, LTOH, STNZ, SETV, WRAC)),						// SWAB(AC) -> AC, N, Z, V
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new CMC("AL0111",	cs(RDCR, LTOL), 7, 1,							"DEC"),		// if CR(7) = 1 then GOTO DEC
		new CMC("AL01110",	cs(RDCR, LTOL), 6, 1,							"NEG"),		// if CR(6) = 1 then GOTO NEG
		new omc("INC",		cs(RDAC, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),			// AC + 1 -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("NEG",		cs(RDAC, COML, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),	// ~AC + 1 -> AC, N, Z, V, C
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("DEC",		cs(RDAC, COMR, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),			// AC + ~0 -> AC, N, Z, V, C
		new CMC("AL1XXX",	cs(RDCR, HTOL), 2, 1,							"AL11XX"),	// if CR(10) = 1 then AL11XX
		new omc("AL10XX",	cs(RDSP, HTOH, LTOL, WRAR)),									// SP -> AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR
		new CMC(			cs(RDCR, HTOL), 1, 1,							"AL101X"),	// if CR(9) = 1 then AL101X
		new CMC(			cs(RDCR, HTOL), 0, 1,							"POPF"),	// if CR(8) = 1 then POPF
		new omc("POP",		cs(RDDR, HTOH, LTOL, WRAC)),									// DR -> AC
		new omc("INCSP",	cs(RDSP, PLS1, HTOH, LTOL, WRSP)),							// SP + 1 -> SP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("POPF",		cs(RDDR, HTOH, LTOL, WRPS)),									// DR -> PS
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INCSP"),	// GOTO INCSP
		new CMC("AL101X",	cs(RDCR, HTOL), 0, 1,							"IRET"),	// if CR(8) = 1 then GOTO IRET
		new omc("RET",		cs(RDDR, HTOH, LTOL, WRIP)),									// DR -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INCSP"),	// GOTO INCSP
		new omc("IRET",		cs(RDDR, HTOH, LTOL, WRPS)),									// DR -> PS
		new omc(			cs(RDSP, PLS1, HTOH, LTOL, WRSP, WRAR)),						// SP + 1 -> SP, AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"RET"),		// GOTO RET
		new CMC("AL11XX",	cs(RDCR, HTOL), 1, 1,							"AL111X"),	// if CR(9) = 1 then GOTO AL111X
		new CMC("AL110X",	cs(RDCR, HTOL), 0, 1,							"PUSHF"),	// if CR(8) = 1 then GOTO PUSHF
		new omc("PUSH",		cs(RDAC, HTOH, LTOL, WRDR)),									// AC -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"PUSHVAL"),	// GOTO PUSHVAL
		new omc("PUSHF",	cs(RDPS, HTOH, LTOL, WRDR)),									// PS -> DR
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"PUSHVAL"),	// GOTO PUSHVAL
		new CMC("AL111X",	cs(RDCR, HTOL), 0, 1,							"RESERVED"),// if CR(8) = 1 then RESERVED
		new omc("SWAP",		cs(RDSP, HTOH, LTOL, WRAR)),									// SP -> AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR
		new omc(			cs(RDDR, HTOH, LTOL, WRBR)),									// DR -> BR
		new omc(			cs(RDAC, HTOH, LTOL, WRDR)),									// AC -> DR
		new omc(			cs(RDBR, HTOH, LTOL, WRBR, STOR)),							// BR -> AC; DR -> MEM(AR)
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		// IO
		new omc("IO",		cs(IO)),													// IO

		// Цикл прерывания
		new CMC("INT",		cs(RDPS, HTOL), RUN.ordinal() - 8, 0,			"HLT"),		// if RUN = 0 then GOTO HLT
		new CMC(			cs(RDPS, HTOL), INTR.ordinal() - 8, 0,			"BEGIN"),	// if INT = 0 then GOTO BEGIN
		new omc(			cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),						// SP + ~0 -> SP, AR
		new omc(			cs(RDIP, HTOH, LTOL, WRDR)),									// IP -> DR
		new omc(			cs(STOR)),													// DR -> MEM(AR)
		new omc(			cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),						// SP + ~0 -> SP, AR
		new omc(			cs(RDPS, HTOH, LTOL, WRDR)),									// PS -> DR
		new omc(			cs(STOR)),													// DR -> MEM(AR)
		new omc(			cs(WRAR)),													// 0 -> AR
		new omc(			cs(LOAD)),													// MEM(AR) -> DR
		new omc(			cs(RDDR, HTOH, LTOL, WRIP, DINT)),							// DR -> IP; DI
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"BEGIN"),	// GOTO BEGIN

		// Ввод адреса
		new omc("SETIP",	cs(RDIR, HTOH, LTOL, WRIP)),									// IR -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"HLT"),		// GOTO HLT
		// Запись
		new omc("WRITE",	cs(RDIP, HTOH, LTOL, WRAR)),									// IP -> AR
		new omc(			cs(RDIR, HTOH, LTOL, WRDR)),									// IR -> DR
		new omc(			cs(RDIP, PLS1, HTOH, LTOL, WRIP, STOR)),						// DR -> MEM(AR); IP + 1 -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"HLT"),		// GOTO HLT
		// Чтение
		new omc("READ",		cs(RDIP, HTOH, LTOL, WRAR)),									// IP -> AR
		new omc(			cs(RDIR, HTOH, LTOL, WRDR)),									// IR -> DR
		new omc(			cs(RDIP, PLS1, HTOH, LTOL, WRIP, LOAD)),						// MEM(AR) -> DR; IP + 1 -> IP
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"HLT"),		// GOTO HLT
		new omc("START",	cs(STNZ, SETV, SETC, WRAC, WRSP, DINT, CLRF)),				//	0 -> AC, SP, C, N, Z, V; DI; CLRF
		new CMC(			cs(RDPS, LTOL), PS0.ordinal(), 0,				"INT"),		// GOTO INT
		new omc("RESERVED",	cs())
	};

	public int getMicroCodeLength() {
		return MP.length;
	}

	public long getMicroCommand(int addr) throws Exception {
		return MP[addr].getMicroCommand();
	}

	public int findLabel(String label) throws Exception {
		for (int addr = 0; addr < MP.length; addr++)
			if (label.equals(MP[addr].label))
				return addr;

		throw new Exception("Label '" + label + "' not found");
	}
}
